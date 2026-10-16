use kmp_terminal_rs::{Osc52Policy, PackedCell, Palette, Terminal};

fn main() {
    println!("========================================================");
    println!("     KMP Terminal Emulator — Rust FFI Showcase Demo     ");
    println!("========================================================");

    // 1. Instantiate terminal emulator (10 rows, 48 cols, 500 scrollback lines)
    let rows = 10;
    let cols = 48;
    let max_scrollback = 500;
    let mut term = Terminal::with_config(rows, cols, max_scrollback, Osc52Policy::AlwaysAllow);

    // 2. Feed rich ANSI payload:
    //    - Window title: \x1b]2;Rust Showcase Terminal\x07
    //    - Bell: \x07
    //    - OSC 52 clipboard write: SGVsbG8gZnJvbSBSdXN0IEZGSSA8MyE= ("Hello from Rust FFI <3!")
    //    - TrueColor 24-bit RGB banner, styled status lines
    let payload = concat!(
        "\x1b]2;Rust Showcase Terminal\x07",
        "\x07",
        "\x1b]52;c;SGVsbG8gZnJvbSBSdXN0IEZGSSA8MyE=\x07",
        "\x1b[38;2;255;215;0;48;2;30;30;50m *** KMP TERMINAL RUST FFI DEMO *** \x1b[0m\r\n",
        "\x1b[1;32m[OK]\x1b[0m Kotlin/Native libterminal_core loaded\r\n",
        "\x1b[1;34m[INFO]\x1b[0m \x1b[3mZero-allocation grid slice fill active\x1b[0m\r\n",
        "\x1b[1;33m[WARN]\x1b[0m \x1b[4mChecking TrueColor & OSC 52 pipeline\x1b[0m\r\n",
        "\x1b[1;35m[OSC52]\x1b[0m Base64 clipboard synchronized\r\n",
        "\x1b[1;36m[PALETTE]\x1b[0m Nord color scheme rendered\r\n",
        "Type: Monospace Grid [10x48] | Scrollback: 500"
    );

    term.process_str(payload);

    // 3. Pre-allocated buffer for zero-allocation grid read
    let total_cells = (rows * cols) as usize;
    let mut buffer = vec![
        PackedCell {
            codepoint: ' ' as i32,
            fg_rgb: 0,
            bg_rgb: -1,
            attributes: 0,
        };
        total_cells
    ];

    let filled = term.read_grid_into(&mut buffer, Palette::Nord);
    println!("\n[Grid Render: {}x{} ({} cells populated)]", rows, cols, filled);

    // Render Unicode box frame
    println!("┌{}┐", "─".repeat(cols as usize));
    for r in 0..rows {
        print!("│");
        for c in 0..cols {
            let idx = (r * cols + c) as usize;
            let ch = buffer[idx].ch();
            print!("{}", ch);
        }
        println!("│");
    }
    println!("└{}┘", "─".repeat(cols as usize));

    // 4. Inspect terminal state & diagnostics
    let (cursor_r, cursor_c) = term.cursor_pos();
    println!("\n--- Terminal State Diagnostics ---");
    println!("• Dimensions:           {} rows x {} cols", term.rows(), term.cols());
    println!("• Cursor Position:      ({}, {})", cursor_r, cursor_c);
    println!("• Cursor Visible:       {}", term.cursor_visible());
    println!("• Bell Event Count:     {}", term.bell_count());
    println!("• Window Title:         {:?}", term.window_title());
    println!("• OSC 52 Write Count:   {}", term.clipboard_write_count());
    println!("• Last OSC 52 Payload:  {:?}", term.last_clipboard_write());

    // 5. Inspect sample cell styles from row 0 and row 1
    println!("\n--- Cell Inspection Samples ---");
    if filled > 5 {
        let title_cell = &buffer[5];
        println!(
            "• Banner Cell [0, 5] ('{}'): FG=0x{:06X}, BG=0x{:06X}, Bold={}, Italic={}",
            title_cell.ch(),
            title_cell.fg_rgb,
            title_cell.bg_rgb,
            title_cell.attrs().bold(),
            title_cell.attrs().italic(),
        );
    }
    if filled > cols as usize + 1 {
        let status_cell = &buffer[cols as usize + 1]; // Line 1 "[OK]" 'O'
        println!(
            "• Status Cell [1, 1] ('{}'): FG=0x{:06X}, BG=0x{:06X}, Bold={}, Underline={}",
            status_cell.ch(),
            status_cell.fg_rgb,
            status_cell.bg_rgb,
            status_cell.attrs().bold(),
            status_cell.attrs().underline(),
        );
    }
    println!("\nShowcase execution completed successfully.");
}
