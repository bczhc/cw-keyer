//! Timeline visualizer for the cw-keyer pipeline.
//!
//! Reads events from stdin and renders a real-time scrolling timeline with three
//! lanes: DIT (blue), DAH (orange), and AUDIO (green).
//!
//! ## stdin protocol (one event per line)
//!
//! Two formats are supported — detected by the presence of a double-space
//! separator:
//!
//! ### Real-time pipe (from `cw-keyer` binary)
//! Each line is a bare event name; the timestamp is taken from the wall clock of
//! this process:
//!
//! ```text
//! dit down
//! dit up
//! dah down
//! dah up
//! key on
//! key off
//! ```
//!
//! ### Log-file replay
//! Each line carries an `HH:MM:SS.mmm` timestamp followed by two spaces and the
//! event name:
//!
//! ```text
//! 00:00:00.123  dit down
//! 00:00:00.185  key on
//! 00:00:02.091  key off
//! ```
//!
//! Any line that does not match one of the six recognised event names is
//! silently ignored, which allows the visualizer to sit downstream of the keyer
//! binary (which also outputs `dit`/`dah`/`char`/`word` semantic events that
//! are irrelevant for timeline display).

use std::io::{self, BufRead};
use std::sync::{Arc, Mutex};

use winit::application::ApplicationHandler;
use winit::event::{WindowEvent, MouseScrollDelta, ElementState};
use winit::event_loop::{ActiveEventLoop, ControlFlow, EventLoop};
use winit::keyboard::{KeyCode, PhysicalKey};
use winit::window::{Window, WindowAttributes};

// ── data model ──────────────────────────────────────────────────────

#[derive(Debug, Clone)]
enum EventMsg {
    DitDown, DitUp, DahDown, DahUp, KeyOn, KeyOff,
}

#[derive(Debug, Clone)]
struct TimedEvent { time: f64, msg: EventMsg }

#[derive(Debug, Clone, Copy)]
enum Lane { Dit, Dah, Audio }

#[derive(Debug, Clone, Copy)]
struct Interval { start: f64, end: f64, lane: Lane }

struct Model {
    events: Vec<TimedEvent>,
    intervals: Vec<Interval>,
}

impl Model {
    fn new() -> Self { Self { events: vec![], intervals: vec![] } }

    fn push(&mut self, ev: TimedEvent) {
        self.events.push(ev);
    }

    fn rebuild_intervals(&mut self, now: f64) {
        self.intervals.clear();
        let (mut dit_s, mut dah_s, mut aud_s): (Option<f64>, Option<f64>, Option<f64>) = (None, None, None);
        for ev in &self.events {
            match ev.msg {
                EventMsg::DitDown => { if dit_s.is_none() { dit_s = Some(ev.time); } }
                EventMsg::DitUp => { if let Some(s) = dit_s.take() { self.intervals.push(Interval { start: s, end: ev.time, lane: Lane::Dit }); } }
                EventMsg::DahDown => { if dah_s.is_none() { dah_s = Some(ev.time); } }
                EventMsg::DahUp => { if let Some(s) = dah_s.take() { self.intervals.push(Interval { start: s, end: ev.time, lane: Lane::Dah }); } }
                EventMsg::KeyOn => { if aud_s.is_none() { aud_s = Some(ev.time); } }
                EventMsg::KeyOff => { if let Some(s) = aud_s.take() { self.intervals.push(Interval { start: s, end: ev.time, lane: Lane::Audio }); } }
            }
        }
        // active (un-matched) intervals extend to `now`
        if let Some(s) = dit_s { self.intervals.push(Interval { start: s, end: now, lane: Lane::Dit }); }
        if let Some(s) = dah_s { self.intervals.push(Interval { start: s, end: now, lane: Lane::Dah }); }
        if let Some(s) = aud_s { self.intervals.push(Interval { start: s, end: now, lane: Lane::Audio }); }
    }
}

fn parse_line(line: &str, start: &std::time::Instant) -> Option<TimedEvent> {
    // "HH:MM:SS.mmm  event_name" (log file) or "event_name" (real-time pipe)
    let (time, msg) = if let Some(cap) = line.split_once("  ") {
        (parse_timestamp(cap.0)?, cap.1.trim())
    } else {
        (start.elapsed().as_secs_f64(), line.trim())
    };
    let msg = match msg {
        "dit down" => EventMsg::DitDown, "dit up" => EventMsg::DitUp,
        "dah down" => EventMsg::DahDown, "dah up" => EventMsg::DahUp,
        "key on" => EventMsg::KeyOn, "key off" => EventMsg::KeyOff,
        _ => return None,
    };
    Some(TimedEvent { time, msg })
}

fn parse_timestamp(s: &str) -> Option<f64> {
    let (hms, ms) = s.split_once('.')?;
    let parts: Vec<&str> = hms.split(':').collect();
    if parts.len() != 3 { return None; }
    let h: f64 = parts[0].parse().ok()?;
    let m: f64 = parts[1].parse().ok()?;
    let s: f64 = parts[2].parse().ok()?;
    let ms: f64 = format!("0.{}", ms).parse().ok()?;
    Some(h * 3600.0 + m * 60.0 + s + ms)
}

// ── render constants ────────────────────────────────────────────────

const DIT_COLOR:   [f32; 4] = [0x4f as f32 / 255.0, 0xc3 as f32 / 255.0, 0xf7 as f32 / 255.0, 1.0];  // #4fc3f7
const DAH_COLOR:   [f32; 4] = [0xff as f32 / 255.0, 0xb7 as f32 / 255.0, 0x4d as f32 / 255.0, 1.0];  // #ffb74d
const AUDIO_COLOR: [f32; 4] = [0x81 as f32 / 255.0, 0xc7 as f32 / 255.0, 0x84 as f32 / 255.0, 1.0];  // #81c784
const GRID_COLOR:  [f32; 4] = [0x2a as f32 / 255.0, 0x2a as f32 / 255.0, 0x4a as f32 / 255.0, 1.0];  // #2a2a4a
const CURSOR_COLOR: [f32; 4] = [1.0, 1.0, 1.0, 0.5];

const LANE_X: [f32; 3] = [100.0, 160.0, 220.0];
const LANE_W: f32 = 50.0;
const MARGIN_LEFT: f32 = 90.0;
const PADDING_TOP: f32 = 24.0;
const DEFAULT_PX_PER_SEC: f32 = 300.0;

// ── wgpu types ─────────────────────────────────────────────────────

#[repr(C)]
#[derive(Copy, Clone, Debug, bytemuck::Pod, bytemuck::Zeroable)]
struct RectInstance { pos: [f32; 2], size: [f32; 2], color: [f32; 4] }

#[repr(C)]
#[derive(Copy, Clone, Debug, bytemuck::Pod, bytemuck::Zeroable)]
struct Uniforms { size: [f32; 2] }

const SHADER: &str = r#"
struct RectInstance { pos: vec2<f32>, size: vec2<f32>, color: vec4<f32> }
struct Uniforms { size: vec2<f32> }

@group(0) @binding(0) var<uniform> u: Uniforms;

struct VertexOutput {
    @builtin(position) pos: vec4<f32>,
    @location(0) color: vec4<f32>,
}

@vertex
fn vs_main(
    @location(0) vpos: vec2<f32>,
    @location(1) inst_pos: vec2<f32>,
    @location(2) inst_size: vec2<f32>,
    @location(3) inst_color: vec4<f32>,
) -> VertexOutput {
    let clip_x = (inst_pos.x + vpos.x * inst_size.x) / u.size.x * 2.0 - 1.0;
    let clip_y = 1.0 - (inst_pos.y + vpos.y * inst_size.y) / u.size.y * 2.0;
    return VertexOutput(vec4<f32>(clip_x, clip_y, 0.0, 1.0), inst_color);
}

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
    return in.color;
}
"#;

// ── gpu resources ────────────────────────────────────────────────

struct Gpu {
    surface: wgpu::Surface<'static>,
    device: wgpu::Device,
    queue: wgpu::Queue,
    config: wgpu::SurfaceConfiguration,
    render_pipeline: wgpu::RenderPipeline,
    uniform_buf: wgpu::Buffer,
    uniform_bind_group: wgpu::BindGroup,
    quad_vertex_buf: wgpu::Buffer,
    quad_index_buf: wgpu::Buffer,
}

impl Gpu {
    async fn new(window: Arc<Window>) -> Self {
        let size = window.inner_size();
        let instance = wgpu::Instance::new(wgpu::InstanceDescriptor {
            backends: wgpu::Backends::all(),
            flags: wgpu::InstanceFlags::default(),
            memory_budget_thresholds: Default::default(),
            backend_options: wgpu::BackendOptions::default(),
            display: None,
        });
        let surface: wgpu::Surface<'_> = instance.create_surface(window).unwrap();
        let surface: wgpu::Surface<'static> = unsafe { std::mem::transmute(surface) };
        let adapter = instance
            .request_adapter(&wgpu::RequestAdapterOptions {
                compatible_surface: Some(&surface),
                ..Default::default()
            })
            .await.unwrap();
        let (device, queue) = adapter
            .request_device(&wgpu::DeviceDescriptor::default())
            .await.unwrap();

        let caps = surface.get_capabilities(&adapter);
        let format = caps.formats[0].remove_srgb_suffix();

        let config = wgpu::SurfaceConfiguration {
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
            format,
            width: size.width, height: size.height,
            present_mode: wgpu::PresentMode::AutoVsync,
            alpha_mode: wgpu::CompositeAlphaMode::Auto,
            view_formats: vec![],
            desired_maximum_frame_latency: 2,
        };
        surface.configure(&device, &config);

        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: None, source: wgpu::ShaderSource::Wgsl(SHADER.into()),
        });

        let uniform_buf = device.create_buffer(&wgpu::BufferDescriptor {
            label: None,
            size: std::mem::size_of::<Uniforms>() as u64,
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });

        let bind_group_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: None,
            entries: &[wgpu::BindGroupLayoutEntry {
                binding: 0, visibility: wgpu::ShaderStages::VERTEX,
                ty: wgpu::BindingType::Buffer {
                    ty: wgpu::BufferBindingType::Uniform,
                    has_dynamic_offset: false, min_binding_size: None,
                },
                count: None,
            }],
        });

        let uniform_bind_group = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: None, layout: &bind_group_layout,
            entries: &[wgpu::BindGroupEntry {
                binding: 0, resource: uniform_buf.as_entire_binding(),
            }],
        });

        let pipeline_layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: None,
            bind_group_layouts: &[Some(&bind_group_layout)],
            immediate_size: 0,
        });

        let render_pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: None, layout: Some(&pipeline_layout),
            vertex: wgpu::VertexState {
                module: &shader, entry_point: Some("vs_main"),
                compilation_options: Default::default(),
                buffers: &[
                    wgpu::VertexBufferLayout {
                        array_stride: 2 * 4, step_mode: wgpu::VertexStepMode::Vertex,
                        attributes: &wgpu::vertex_attr_array![0 => Float32x2],
                    },
                    wgpu::VertexBufferLayout {
                        array_stride: std::mem::size_of::<RectInstance>() as u64,
                        step_mode: wgpu::VertexStepMode::Instance,
                        attributes: &wgpu::vertex_attr_array![1 => Float32x2, 2 => Float32x2, 3 => Float32x4],
                    },
                ],
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader, entry_point: Some("fs_main"),
                compilation_options: Default::default(),
                targets: &[Some(wgpu::ColorTargetState {
                    format: config.format,
                    blend: Some(wgpu::BlendState::ALPHA_BLENDING),
                    write_mask: wgpu::ColorWrites::ALL,
                })],
            }),
            primitive: Default::default(),
            multisample: Default::default(),
            depth_stencil: None,
            multiview_mask: None,
            cache: None,
        });

        let quad_verts: [[f32; 2]; 4] = [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]];
        let quad_indices: [u16; 6] = [0, 1, 2, 0, 2, 3];

        let quad_vertex_buf = device.create_buffer(&wgpu::BufferDescriptor {
            label: None,
            size: (quad_verts.len() * std::mem::size_of::<[f32; 2]>()) as u64,
            usage: wgpu::BufferUsages::VERTEX | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: true,
        });
        quad_vertex_buf.slice(..).get_mapped_range_mut().copy_from_slice(bytemuck::cast_slice(&quad_verts));
        quad_vertex_buf.unmap();

        let quad_index_buf = device.create_buffer(&wgpu::BufferDescriptor {
            label: None,
            size: (quad_indices.len() * std::mem::size_of::<u16>()) as u64,
            usage: wgpu::BufferUsages::INDEX | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: true,
        });
        quad_index_buf.slice(..).get_mapped_range_mut().copy_from_slice(bytemuck::cast_slice(&quad_indices));
        quad_index_buf.unmap();

        Self { surface, device, queue, config, render_pipeline, uniform_buf, uniform_bind_group, quad_vertex_buf, quad_index_buf }
    }

    fn resize(&mut self, w: u32, h: u32) {
        if w == 0 || h == 0 { return; }
        self.config.width = w; self.config.height = h;
        self.surface.configure(&self.device, &self.config);
    }

    fn render(&mut self, instances: &[RectInstance], size: (u32, u32)) {
        let uniforms = Uniforms { size: [size.0 as f32, size.1 as f32] };
        self.queue.write_buffer(&self.uniform_buf, 0, bytemuck::bytes_of(&uniforms));

        let instance_buf = if instances.is_empty() { None } else {
            let buf = self.device.create_buffer(&wgpu::BufferDescriptor {
                label: None,
                size: (instances.len() * std::mem::size_of::<RectInstance>()) as u64,
                usage: wgpu::BufferUsages::VERTEX | wgpu::BufferUsages::COPY_DST,
                mapped_at_creation: true,
            });
            buf.slice(..).get_mapped_range_mut().copy_from_slice(bytemuck::cast_slice(instances));
            buf.unmap();
            Some(buf)
        };

        let output = match self.surface.get_current_texture() {
            wgpu::CurrentSurfaceTexture::Success(tex)
            | wgpu::CurrentSurfaceTexture::Suboptimal(tex) => tex,
            _ => return,
        };
        let view = output.texture.create_view(&wgpu::TextureViewDescriptor::default());
        let mut encoder = self.device.create_command_encoder(&wgpu::CommandEncoderDescriptor::default());

        {
            let mut pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: None,
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: &view, resolve_target: None,
                    ops: wgpu::Operations {
                        load: wgpu::LoadOp::Clear(wgpu::Color { r: 0x1a as f64 / 255.0, g: 0x1a as f64 / 255.0, b: 0x2e as f64 / 255.0, a: 1.0 }), // #1a1a2e
                        store: wgpu::StoreOp::Store,
                    },
                    depth_slice: None,
                })],
                depth_stencil_attachment: None,
                timestamp_writes: None,
                occlusion_query_set: None,
                multiview_mask: None,
            });

            pass.set_pipeline(&self.render_pipeline);
            pass.set_bind_group(0, &self.uniform_bind_group, &[]);
            pass.set_vertex_buffer(0, self.quad_vertex_buf.slice(..));
            if let Some(ref buf) = instance_buf {
                pass.set_vertex_buffer(1, buf.slice(..));
                pass.set_index_buffer(self.quad_index_buf.slice(..), wgpu::IndexFormat::Uint16);
                pass.draw_indexed(0..6, 0, 0..instances.len() as u32);
            }
        }

        self.queue.submit([encoder.finish()]);
        output.present();
    }
}

// ── helper: build visible rectangles ─────────────────────────────

fn build_instances(model: &Model, view_top: f64, px_per_sec: f32, window_h: f32, now: f64) -> Vec<RectInstance> {
    let mut instances = Vec::new();
    let view_bottom = view_top + window_h as f64 / px_per_sec as f64;

    let grid_step = 0.5_f64;
    let mut t = (view_top / grid_step).floor() * grid_step;
    while t <= view_bottom {
        let y = ((t - view_top) * px_per_sec as f64) as f32;
        instances.push(RectInstance { pos: [0.0, y - 0.5], size: [700.0, 1.0], color: GRID_COLOR });
        t += grid_step;
    }

    for (i, (x, _)) in LANE_X.iter().zip(["DIT", "DAH", "AUDIO"]).enumerate() {
        let h = window_h;
        let col = match i { 0 => DIT_COLOR, 1 => DAH_COLOR, _ => AUDIO_COLOR };
        instances.push(RectInstance { pos: [*x, PADDING_TOP], size: [LANE_W, h - PADDING_TOP], color: [0.0, 0.0, 0.0, 0.0] });
        instances.push(RectInstance { pos: [*x + 2.0, 4.0], size: [LANE_W - 4.0, 14.0], color: [col[0], col[1], col[2], 0.5] });
    }

    for iv in &model.intervals {
        if iv.end < view_top || iv.start > view_bottom { continue; }
        let x = LANE_X[iv.lane as usize];
        let y = ((iv.start - view_top) * px_per_sec as f64) as f32;
        let h = ((iv.end - iv.start) * px_per_sec as f64) as f32;
        let color = match iv.lane {
            Lane::Dit  => DIT_COLOR,
            Lane::Dah  => DAH_COLOR,
            Lane::Audio => AUDIO_COLOR,
        };
        instances.push(RectInstance { pos: [x, y], size: [LANE_W, h.max(1.0)], color });
    }

    let y = ((now - view_top) * px_per_sec as f64) as f32;
    instances.push(RectInstance { pos: [MARGIN_LEFT, y - 1.0], size: [700.0 - MARGIN_LEFT, 3.0], color: CURSOR_COLOR });

    instances
}

// ── app state ────────────────────────────────────────────────────

struct App {
    model: Arc<Mutex<Model>>,
    gpu: Option<Gpu>,
    follow: bool,
    scroll_offset: f64,
    window: Option<Arc<Window>>,
    start: std::time::Instant,
}

impl ApplicationHandler for App {
    fn resumed(&mut self, event_loop: &ActiveEventLoop) {
        if self.window.is_none() {
            let attr = WindowAttributes::default()
                .with_title("cw-keyer timeline visualizer")
                .with_inner_size(winit::dpi::PhysicalSize::new(800, 600));
            let window = Arc::new(event_loop.create_window(attr).unwrap());
            self.gpu = Some(pollster::block_on(Gpu::new(window.clone())));
            self.window = Some(window);
        }
    }

    fn window_event(&mut self, event_loop: &ActiveEventLoop, _id: winit::window::WindowId, event: WindowEvent) {
        let gpu = self.gpu.as_mut().unwrap();
        let window = self.window.as_ref().unwrap();

        match event {
            WindowEvent::CloseRequested => event_loop.exit(),
            WindowEvent::Resized(size) => gpu.resize(size.width, size.height),
            WindowEvent::MouseWheel { delta, .. } => {
                let dy = match delta {
                    MouseScrollDelta::LineDelta(_, y) => y as f64 * 0.2,
                    MouseScrollDelta::PixelDelta(pos) => pos.y * 0.005,
                };
                self.scroll_offset -= dy;
                self.follow = false;
            }
            WindowEvent::KeyboardInput { event, .. } => {
                if event.state == ElementState::Pressed {
                    if let PhysicalKey::Code(KeyCode::Space) = event.physical_key {
                        self.follow = !self.follow;
                    }
                }
            }
            WindowEvent::RedrawRequested => {
                let size = window.inner_size();
                let now = self.start.elapsed().as_secs_f64();
                let mut model = self.model.lock().unwrap();

                model.rebuild_intervals(now);

                let px_per_sec = DEFAULT_PX_PER_SEC as f64;
                let view_window = size.height as f64 / px_per_sec;

                let view_top = if self.follow {
                    let top = (now - view_window * 0.85).max(0.0);
                    self.scroll_offset = top;
                    top
                } else {
                    self.scroll_offset
                };

                let instances = build_instances(&model, view_top, DEFAULT_PX_PER_SEC, size.height as f32, now);
                drop(model);

                let _ = gpu.render(&instances, (size.width, size.height));
            }
            _ => {}
        }
    }

    fn about_to_wait(&mut self, _event_loop: &ActiveEventLoop) {
        if let Some(window) = &self.window {
            window.request_redraw();
        }
    }
}

// ── main ─────────────────────────────────────────────────────────

fn main() {
    env_logger::init();

    let model = Arc::new(Mutex::new(Model::new()));
    let model_clone = Arc::clone(&model);
    let start = std::time::Instant::now();
    std::thread::spawn(move || {
        let stdin = io::stdin();
        for line in stdin.lock().lines() {
            if let Ok(l) = line {
                if let Some(ev) = parse_line(&l, &start) {
                    model_clone.lock().unwrap().push(ev);
                }
            }
        }
    });

    let event_loop = EventLoop::new().unwrap();
    event_loop.set_control_flow(ControlFlow::Poll);

    let mut app = App {
        model,
        gpu: None,
        follow: true,
        scroll_offset: 0.0,
        window: None,
        start: std::time::Instant::now(),
    };

    event_loop.run_app(&mut app).unwrap();
}
