use oboe::{AudioOutputCallback, AudioOutputStreamSafe, AudioStream, AudioStreamBuilder, DataCallbackResult, IsFrameType, Mono, PerformanceMode, SharingMode};
use std::f32::consts::PI;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

pub struct SineWave {
    frequency: f32,
    gain: f32,
    phase: f32,
    delta: Option<f32>,
    playing: Arc<AtomicBool>,
}

impl SineWave {
    pub fn new(frequency: f32, gain: f32, playing: Arc<AtomicBool>) -> Self {
        Self {
            frequency,
            gain,
            phase: 0.0,
            delta: None,
            playing,
        }
    }
}

impl AudioOutputCallback for SineWave {
    type FrameType = (f32, Mono);

    fn on_audio_ready(
        &mut self,
        stream: &mut dyn AudioOutputStreamSafe,
        frames: &mut [<Self::FrameType as IsFrameType>::Type],
    ) -> DataCallbackResult {
        if self.delta.is_none() {
            let sample_rate = stream.get_sample_rate() as f32;
            self.delta = Some(self.frequency * 2.0 * PI / sample_rate);
        }

        if self.playing.load(Ordering::Relaxed) {
            let delta = self.delta.unwrap();
            for frame in frames {
                *frame = self.gain * self.phase.sin();
                self.phase += delta;
                while self.phase > 2.0 * PI {
                    self.phase -= 2.0 * PI;
                }
            }
        } else {
            for frame in frames.iter_mut() {
                *frame = 0.0;
            }
        }

        DataCallbackResult::Continue
    }
}

pub struct AudioPlayer {
    playing: Arc<AtomicBool>,
    running: Arc<AtomicBool>,
    handle: Option<thread::JoinHandle<()>>,
}

impl AudioPlayer {
    pub fn new(frequency: f32) -> Self {
        let playing = Arc::new(AtomicBool::new(false));
        let running = Arc::new(AtomicBool::new(true));
        let playing_clone = playing.clone();
        let running_clone = running.clone();

        let handle = thread::spawn(move || {
            let wave = SineWave::new(frequency, 0.5, playing_clone);
            let mut stream = AudioStreamBuilder::default()
                .set_performance_mode(PerformanceMode::LowLatency)
                .set_sharing_mode(SharingMode::Shared)
                .set_format::<f32>()
                .set_channel_count::<Mono>()
                .set_callback(wave)
                .open_stream()
                .expect("Failed to open audio stream");
            stream.start().expect("Failed to start audio stream");

            while running_clone.load(Ordering::Relaxed) {
                thread::sleep(Duration::from_millis(100));
            }
            // explicitly drop it to stop the stream
            drop(stream);
        });

        AudioPlayer {
            playing,
            running,
            handle: Some(handle),
        }
    }

    pub fn start_tone(&self) {
        self.playing.store(true, Ordering::Relaxed);
    }

    pub fn stop_tone(&self) {
        self.playing.store(false, Ordering::Relaxed);
    }
}

impl Drop for AudioPlayer {
    fn drop(&mut self) {
        self.running.store(false, Ordering::Relaxed);
        if let Some(handle) = self.handle.take() {
            handle.join().expect("Failed to join audio thread");
        }
    }
}
