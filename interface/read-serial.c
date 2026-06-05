#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <termios.h>
#include <time.h>
#include <unistd.h>
#include <sys/select.h>

static const char *map(uint8_t code) {
	switch (code) {
	case 0x01: return "dit down";
	case 0x02: return "dit up";
	case 0x03: return "dah down";
	case 0x04: return "dah up";
	default:   return NULL;
	}
}

static const char *channel_name(uint8_t code) {
	switch (code) {
	case 0x01: case 0x02: return "dit";
	case 0x03: case 0x04: return "dah";
	default:              return NULL;
	}
}

static uint8_t opposite(uint8_t code) {
	switch (code) {
	case 0x01: return 0x02;
	case 0x02: return 0x01;
	case 0x03: return 0x04;
	case 0x04: return 0x03;
	default:   return 0;
	}
}

static double monotonic_now(void) {
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return (double)ts.tv_sec + (double)ts.tv_nsec * 1e-9;
}

typedef struct {
	uint8_t code;
	double  time;
	bool    active;
} PendingEvent;

static PendingEvent pending_dit = {0, 0.0, false};
static PendingEvent pending_dah = {0, 0.0, false};

static PendingEvent *get_pending(const char *ch) {
	if (strcmp(ch, "dit") == 0) return &pending_dit;
	if (strcmp(ch, "dah") == 0) return &pending_dah;
	return NULL;
}

static void emit(const char *msg) {
	if (msg) {
		printf("%s\n", msg);
		fflush(stdout);
	}
}

static void flush_pending(void) {
	double now = monotonic_now();
	PendingEvent *pends[] = {&pending_dit, &pending_dah, NULL};
	for (int i = 0; pends[i]; i++) {
		PendingEvent *p = pends[i];
		if (p->active && (now - p->time) >= 0.001) {
			emit(map(p->code));
			p->active = false;
		}
	}
}

static int configure_serial(int fd) {
	struct termios tty;
	memset(&tty, 0, sizeof(tty));
	if (tcgetattr(fd, &tty) != 0) {
		perror("tcgetattr");
		return -1;
	}

	cfsetospeed(&tty, B115200);
	cfsetispeed(&tty, B115200);

	tty.c_cflag = (tty.c_cflag & ~CSIZE) | CS8;
	tty.c_cflag &= ~(PARENB | CSTOPB | CRTSCTS);
	tty.c_cflag |= CREAD | CLOCAL;

	tty.c_iflag &= ~(IXON | IXOFF | IXANY | ICRNL | INLCR);
	tty.c_oflag &= ~OPOST;
	tty.c_lflag &= ~(ICANON | ECHO | ECHOE | ISIG);

	tty.c_cc[VMIN]  = 1;
	tty.c_cc[VTIME] = 0;

	if (tcsetattr(fd, TCSANOW, &tty) != 0) {
		perror("tcsetattr");
		return -1;
	}
	return 0;
}

static volatile bool keep_running = true;

static void sigint_handler(int sig) {
	(void)sig;
	keep_running = false;
}

int main(void) {
	const char *port = getenv("SERIAL");
	if (!port) port = "/dev/ttyUSB0";

	int fd = open(port, O_RDWR | O_NOCTTY);
	if (fd < 0) {
		perror("open");
		return 1;
	}

	if (configure_serial(fd) < 0) {
		close(fd);
		return 1;
	}

	FILE *log = fopen("keyer.log", "a");
	if (!log) {
		perror("fopen keyer.log");
		close(fd);
		return 1;
	}
	setvbuf(log, NULL, _IONBF, 0);

	signal(SIGINT,  sigint_handler);
	signal(SIGTERM, sigint_handler);

	while (keep_running) {
		fd_set fds;
		FD_ZERO(&fds);
		FD_SET(fd, &fds);

		struct timeval tv = {0, 1000};

		int ret = select(fd + 1, &fds, NULL, NULL, &tv);
		if (ret < 0) {
			if (errno == EINTR) continue;
			perror("select");
			break;
		}

		flush_pending();

		if (ret == 0) continue;

		if (FD_ISSET(fd, &fds)) {
			uint8_t byte;
			ssize_t n = read(fd, &byte, 1);
			if (n <= 0) break;

			struct timespec ts;
			clock_gettime(CLOCK_REALTIME, &ts);
			time_t t = ts.tv_sec;
			struct tm tm;
			localtime_r(&t, &tm);
			long us = ts.tv_nsec / 1000;

			const char *msg = map(byte);
			fprintf(log, "%02d:%02d:%02d.%06ld  0x%02x  %s\n",
			        tm.tm_hour, tm.tm_min, tm.tm_sec, us,
			        byte, msg ? msg : "?");
			fflush(log);

			const char *ch = channel_name(byte);
			if (!ch) continue;

			PendingEvent *p = get_pending(ch);
			double now = monotonic_now();

			if (!p->active) {
				p->code   = byte;
				p->time   = now;
				p->active = true;
			} else if (byte == opposite(p->code) && (now - p->time) < 0.001) {
				p->active = false;
			} else {
				emit(map(p->code));
				p->code = byte;
				p->time = now;
			}
		}
	}

	fclose(log);
	close(fd);
	return 0;
}
