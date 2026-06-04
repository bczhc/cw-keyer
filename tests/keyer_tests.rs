use cw_keyer::{Keyer, KeyerMode, KeyEvent};

const WPM: f64 = 30.0;

fn dit() -> f64 {
    1.2 / WPM
}

fn dah() -> f64 {
    3.0 * dit()
}

fn ie() -> f64 {
    dit()
}

fn drain(keyer: &mut Keyer, start: f64, duration: f64) -> Vec<(f64, KeyEvent)> {
    let mut out = Vec::new();
    let dt = 0.001;
    let end = start + duration;
    let mut t = start;
    while t <= end {
        for e in keyer.tick(t) {
            out.push((t, e));
        }
        t += dt;
    }
    out
}

/// Extract dit/dah sequence from KeyDown/KeyUp events by duration.
fn key_sequence(events: &[(f64, KeyEvent)]) -> Vec<String> {
    let d = dit();
    let mut seq = Vec::new();
    let mut down: Option<f64> = None;
    for (t, e) in events {
        match e {
            KeyEvent::KeyDown => down = Some(*t),
            KeyEvent::KeyUp => {
                if let Some(down_t) = down.take() {
                    let dur = t - down_t;
                    if (dur - d).abs() < d * 0.4 {
                        seq.push("dit".into());
                    } else {
                        seq.push("dah".into());
                    }
                }
            }
            _ => {}
        }
    }
    seq
}

#[test]
fn ultimatic_squeeze_dah_then_dit() {
    let mut k = Keyer::new(WPM, KeyerMode::Ultimatic);
    k.set_dah(true, 0.0);
    k.set_dit(true, 0.003);
    let events = drain(&mut k, 0.0, (dah() + ie() + dit() + ie()) * 2.0);
    let seq = key_sequence(&events);
    assert!(seq.len() >= 2, "expected at least 2 elements, got {:?}", seq);
    assert_eq!(seq[0], "dah");
    assert_eq!(seq[1], "dit");
}

#[test]
fn ultimatic_quick_tap() {
    let mut k = Keyer::new(WPM, KeyerMode::Ultimatic);
    k.set_dah(true, 0.0);
    k.tick(0.0);

    let tap_t = dah() * 0.5;
    let dt = 0.001;
    let mut t = dt;
    let mut tap_done = false;
    let mut events = Vec::new();
    let end = (dah() + ie()) * 5.0;
    while t <= end {
        if !tap_done && t >= tap_t {
            k.set_dit(true, t);
            k.set_dit(false, t + 0.005);
            tap_done = true;
        }
        for e in k.tick(t) {
            events.push((t, e));
        }
        t += dt;
    }
    let seq = key_sequence(&events);
    assert!(seq.contains(&"dit".into()), "expected a dit from quick tap, got {:?}", seq);
}

#[test]
fn ultimatic_no_extra_on_release() {
    let mut k = Keyer::new(WPM, KeyerMode::Ultimatic);
    k.set_dit(true, 0.0);
    k.set_dah(true, 0.001);

    let dt = 0.001;
    let mut t = 0.0;
    let mut events = Vec::new();
    let release_time = dah() * 2.5;
    let mut released = false;
    let end = (dah() + ie()) * 5.0;
    while t <= end {
        if !released && t >= release_time {
            k.set_dit(false, t);
            k.set_dah(false, t);
            released = true;
        }
        for e in k.tick(t) {
            events.push((t, e));
        }
        t += dt;
    }
    let key_downs_after_release = events
        .iter()
        .filter(|(t, e)| *t >= release_time && matches!(e, KeyEvent::KeyDown))
        .count();
    assert_eq!(key_downs_after_release, 0);
}

#[test]
fn iambic_b_squeeze_alternates() {
    let mut k = Keyer::new(WPM, KeyerMode::IambicB);
    k.set_dit(true, 0.0);
    k.set_dah(true, 0.0);
    let events = drain(&mut k, 0.0, (dit() + ie() + dah() + ie()) * 2.0);
    let seq = key_sequence(&events);
    assert!(seq.len() >= 3, "expected alternating, got {:?}", seq);
}

#[test]
fn iambic_a_squeeze_alternates() {
    let mut k = Keyer::new(WPM, KeyerMode::IambicA);
    k.set_dit(true, 0.0);
    k.set_dah(true, 0.0);
    let events = drain(&mut k, 0.0, (dit() + ie() + dah() + ie()) * 2.0);
    let seq = key_sequence(&events);
    assert!(seq.len() >= 3, "expected alternating, got {:?}", seq);
}

#[test]
fn char_space_event() {
    let mut k = Keyer::new(WPM, KeyerMode::IambicB);
    // One element then release — silence lets char deadline fire
    k.set_dah(true, 0.0);
    let dt = 0.001;
    let mut t = 0.0;
    let mut released = false;
    let mut events = Vec::new();
    let end = dah() + 4.0 * ie() + 0.01;
    while t <= end {
        if !released && t >= dah() * 0.5 {
            k.set_dah(false, t);
            released = true;
        }
        for e in k.tick(t) {
            events.push((t, e));
        }
        t += dt;
    }
    let chars: Vec<_> = events.iter().filter(|(_, e)| matches!(e, KeyEvent::CharSpace)).collect();
    assert!(!chars.is_empty(), "expected CharSpace event, got {:?}", events);
}

#[test]
fn word_space_event() {
    let mut k = Keyer::new(WPM, KeyerMode::IambicB);
    // One element then long silence — word deadline fires
    k.set_dah(true, 0.0);
    let dt = 0.001;
    let mut t = 0.0;
    let mut released = false;
    let mut events = Vec::new();
    let end = dah() + 8.0 * ie() + 0.01;
    while t <= end {
        if !released && t >= dah() * 0.5 {
            k.set_dah(false, t);
            released = true;
        }
        for e in k.tick(t) {
            events.push((t, e));
        }
        t += dt;
    }
    let words: Vec<_> = events.iter().filter(|(_, e)| matches!(e, KeyEvent::WordSpace)).collect();
    assert!(!words.is_empty(), "expected WordSpace event, got {:?}", events);
}

#[test]
fn no_char_while_sending() {
    let mut k = Keyer::new(WPM, KeyerMode::IambicB);
    // Continuous sending — char should NOT fire
    k.set_dah(true, 0.0);
    let events = drain(&mut k, 0.0, (dah() + ie()) * 3.0);
    let chars: Vec<_> = events.iter().filter(|(_, e)| matches!(e, KeyEvent::CharSpace)).collect();
    assert!(chars.is_empty(), "no CharSpace while sending, got {:?}", events);
}

#[test]
fn straight_short_press_is_dit() {
    let mut k = Keyer::new(WPM, KeyerMode::Straight);
    let dt = 0.001;
    let mut t = 0.0;
    let mut events = Vec::new();

    // Press and release quickly (shorter than dah * 2/3)
    k.set_dit(true, 0.0);
    let release_time = dit() * 0.5; // well under the dah*2/3 threshold
    let mut released = false;
    let end = 5.0 * ie();
    while t <= end {
        if !released && t >= release_time {
            k.set_dit(false, t);
            released = true;
        }
        for e in k.tick(t) {
            events.push((t, e));
        }
        t += dt;
    }

    let dits: Vec<_> = events.iter().filter(|(_, e)| matches!(e, KeyEvent::Dit)).collect();
    assert!(!dits.is_empty(), "short hold should emit Dit, got {:?}", events);
    let dahs: Vec<_> = events.iter().filter(|(_, e)| matches!(e, KeyEvent::Dah)).collect();
    assert!(dahs.is_empty(), "short hold should NOT emit Dah, got {:?}", events);
}

#[test]
fn straight_long_press_is_dah() {
    let mut k = Keyer::new(WPM, KeyerMode::Straight);
    let dt = 0.001;
    let mut t = 0.0;
    let mut events = Vec::new();

    // Press and hold longer than dah * 2/3
    k.set_dit(true, 0.0);
    let release_time = dah() * 0.8; // above the threshold (dah * 2/3 ≈ 0.667*dah)
    let mut released = false;
    let end = dah() + 5.0 * ie();
    while t <= end {
        if !released && t >= release_time {
            k.set_dit(false, t);
            released = true;
        }
        for e in k.tick(t) {
            events.push((t, e));
        }
        t += dt;
    }

    let dahs: Vec<_> = events.iter().filter(|(_, e)| matches!(e, KeyEvent::Dah)).collect();
    assert!(!dahs.is_empty(), "long hold should emit Dah, got {:?}", events);
}

#[test]
fn straight_key_down_on_press_up_on_release() {
    let mut k = Keyer::new(WPM, KeyerMode::Straight);

    // Press
    k.set_dit(true, 0.0);
    let ev = k.tick(0.0);
    assert!(ev.iter().any(|e| matches!(e, KeyEvent::KeyDown)), "should emit KeyDown on press, got {:?}", ev);
    assert!(!ev.iter().any(|e| matches!(e, KeyEvent::KeyUp)), "no KeyUp yet");

    // Release
    k.set_dit(false, 0.1);
    let ev = k.tick(0.1);
    assert!(ev.iter().any(|e| matches!(e, KeyEvent::KeyUp)), "should emit KeyUp on release");
}
