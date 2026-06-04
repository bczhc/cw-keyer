#define PIN_A 5
#define PIN_B 4

struct Channel {
  uint8_t pin;
  int last;
  uint8_t code_on;
  uint8_t code_off;
};

Channel channels[] = {
  { PIN_A, HIGH, 0x01, 0x02 },
  { PIN_B, HIGH, 0x03, 0x04 },
};

void setup() {
  Serial.begin(115200);
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, HIGH);
  for (auto &ch : channels) {
    pinMode(ch.pin, INPUT_PULLUP);
    ch.last = digitalRead(ch.pin);
  }
}

void loop() {
  bool led_on = false;
  for (auto &ch : channels) {
    int val = digitalRead(ch.pin);
    if (val != ch.last) {
      Serial.write(val == LOW ? ch.code_on : ch.code_off);
      ch.last = val;
    }
    if (val == LOW) led_on = true;
  }
  digitalWrite(LED_BUILTIN, led_on ? LOW : HIGH);
  delay(1);
}
