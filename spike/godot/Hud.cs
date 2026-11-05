using System;
using System.Collections.Generic;
using Godot;
using Samjae.Core;

namespace Samjae.Client;

/// <summary>Colours from the design's tokens, kept here so the whole HUD is themed from one place.</summary>
public static class HudTokens
{
    public static readonly Color Wood = new("2a1c12");
    public static readonly Color WoodDeep = new("16100a");
    public static readonly Color Slot = new("1e150d");
    public static readonly Color Gold = new("c9a85a");
    public static readonly Color GoldBright = new("f2d58a");
    public static readonly Color Paper = new("e8dfc6");
    public static readonly Color Ink = new("2a2118");
    public static readonly Color Seal = new("9c2f1f");
    public static readonly Color Hp = new("6f9a5a");
    public static readonly Color Muted = new("a89878");

    public static StyleBoxFlat Box(Color fill, Color border, int width = 2, int radius = 3)
    {
        var box = new StyleBoxFlat { BgColor = fill, BorderColor = border, AntiAliasing = true };
        box.SetBorderWidthAll(width);
        box.SetCornerRadiusAll(radius);
        box.SetContentMarginAll(8);
        return box;
    }
}

/// <summary>One in-game HUD screen following the design's layout: top bar, alerts, minimap, selection and command card.</summary>
public partial class Hud : CanvasLayer
{
    private const int BottomHeight = 208;
    private const int TopHeight = 52;

    private static readonly (string Label, string Key)[] Commands =
    {
        ("이동", "M"), ("정지", "S"), ("공격", "A"), ("위치 고수", "H"),
        ("순찰", "P"), ("일제 사격", "V"), ("화약 보충", "R"), ("", ""),
        ("", ""), ("", ""), ("", ""), ("취소", "Esc"),
    };

    private Label _clock = null!;
    private Label _sync = null!;
    private Label _title = null!;
    private Label _hpText = null!;
    private ProgressBar _hpBar = null!;
    private Label _state = null!;
    private GridContainer _roster = null!;
    private TextureRect _portrait = null!;
    private Art _art = null!;
    private int _shownSelection = -1;

    public Minimap Minimap { get; private set; } = null!;

    public void Build(Simulation simulation, WorldView world, Art art, Font body, Font display)
    {
        _art = art;
        var root = new Control { MouseFilter = Control.MouseFilterEnum.Ignore };
        root.SetAnchorsPreset(Control.LayoutPreset.FullRect);
        root.Theme = new Theme { DefaultFont = body, DefaultFontSize = 14 };
        AddChild(root);

        root.AddChild(BuildTopBar(display));
        root.AddChild(BuildAlerts(display));
        root.AddChild(BuildBottomBar(simulation, world, display));
    }

    public void Refresh(int tick, ulong hash, double fps, IReadOnlyCollection<int> selected, Simulation simulation)
    {
        int seconds = tick / Simulation.TicksPerSecond;
        int hundredths = tick % Simulation.TicksPerSecond * 100 / Simulation.TicksPerSecond;
        _clock.Text = $"{seconds / 60:00}:{seconds % 60:00}.{hundredths / 10}   T {tick:000000}";
        _sync.Text = $"동기화 0x{hash >> 48:X4}   {fps:0} FPS";
        if (selected.Count != _shownSelection)
        {
            ShowSelection(selected, simulation);
        }

        Minimap.QueueRedraw();
    }

    private void ShowSelection(IReadOnlyCollection<int> selected, Simulation simulation)
    {
        _shownSelection = selected.Count;
        bool any = selected.Count > 0;
        _title.Text = any ? $"화승총수  ×{selected.Count}" : "선택 없음";
        _hpBar.Value = any ? 78 : 0;
        _hpText.Text = any ? $"체력   {212 * selected.Count} / {270 * selected.Count}" : "체력   -";
        _state.Text = any ? "상태   대기 중\n무기   피해 6 · 사거리 5\n화약   일제 사격 4초 후" : "유닛을 선택하면 여기에 정보가 표시됩니다.";
        _portrait.Modulate = any ? Colors.White : Colors.Transparent;
        foreach (Node child in _roster.GetChildren())
        {
            child.QueueFree();
        }

        int shown = 0;
        foreach (int id in selected)
        {
            if (shown++ >= 12)
            {
                break;
            }

            _roster.AddChild(BuildRosterSlot(id));
        }
    }

    private Control BuildRosterSlot(int id)
    {
        string[] names = { "gunner", "artisan", "brute" };
        var slot = new PanelContainer
        {
            CustomMinimumSize = new Vector2(52, 58),
            MouseFilter = Control.MouseFilterEnum.Ignore,
        };
        slot.AddThemeStyleboxOverride("panel", HudTokens.Box(HudTokens.Slot, HudTokens.Gold, 1));
        slot.AddChild(new TextureRect
        {
            Texture = _art.Sprite(names[id % names.Length]),
            ExpandMode = TextureRect.ExpandModeEnum.IgnoreSize,
            StretchMode = TextureRect.StretchModeEnum.KeepAspectCentered,
            TextureFilter = CanvasItem.TextureFilterEnum.LinearWithMipmaps,
            MouseFilter = Control.MouseFilterEnum.Ignore,
        });
        return slot;
    }

    private Control BuildTopBar(Font display)
    {
        var bar = new PanelContainer { OffsetBottom = TopHeight, MouseFilter = Control.MouseFilterEnum.Stop };
        bar.AnchorRight = 1f;
        bar.AddThemeStyleboxOverride("panel", HudTokens.Box(HudTokens.Wood, HudTokens.Gold, 0));
        var row = new HBoxContainer();
        row.AddThemeConstantOverride("separation", 10);
        bar.AddChild(row);

        row.AddChild(MakeLabel("삼재", display, 26, HudTokens.Paper));
        row.AddChild(MakeLabel("1P · 조선 공방", null, 12, HudTokens.Muted));
        row.AddChild(ResourceChip("귀화석", "540", "+12"));
        row.AddChild(ResourceChip("산기", "80", "+4"));
        row.AddChild(ResourceChip("식량", "14 / 30", ""));
        row.AddChild(new Control { SizeFlagsHorizontal = Control.SizeFlags.ExpandFill });

        var clockPanel = new PanelContainer();
        clockPanel.AddThemeStyleboxOverride("panel", HudTokens.Box(HudTokens.WoodDeep, HudTokens.Gold));
        _clock = MakeLabel("00:00.0", null, 18, HudTokens.GoldBright);
        clockPanel.AddChild(_clock);
        row.AddChild(clockPanel);
        row.AddChild(new Control { SizeFlagsHorizontal = Control.SizeFlags.ExpandFill });

        _sync = MakeLabel("동기화", null, 12, HudTokens.Hp);
        row.AddChild(_sync);
        row.AddChild(SmallButton("단축키 F1"));
        row.AddChild(SmallButton("메뉴 F10"));
        return bar;
    }

    private static Control ResourceChip(string name, string value, string rate)
    {
        var chip = new PanelContainer();
        chip.AddThemeStyleboxOverride("panel", HudTokens.Box(HudTokens.Paper, HudTokens.Gold, 1));
        var row = new HBoxContainer();
        row.AddThemeConstantOverride("separation", 6);
        chip.AddChild(row);
        row.AddChild(MakeLabel(value, null, 16, HudTokens.Ink));
        if (rate.Length > 0)
        {
            row.AddChild(MakeLabel(rate, null, 12, new Color("4f7a3a")));
        }

        row.AddChild(MakeLabel(name, null, 12, new Color("5a4c38")));
        return chip;
    }

    private static Control SmallButton(string text)
    {
        var panel = new PanelContainer();
        panel.AddThemeStyleboxOverride("panel", HudTokens.Box(HudTokens.WoodDeep, new Color("4a3a26"), 1));
        panel.AddChild(MakeLabel(text, null, 12, HudTokens.Paper));
        return panel;
    }

    private static Control BuildAlerts(Font display)
    {
        var column = new VBoxContainer
        {
            AnchorLeft = 1f,
            AnchorRight = 1f,
            OffsetLeft = -296,
            OffsetRight = -16,
            OffsetTop = TopHeight + 18,
            MouseFilter = Control.MouseFilterEnum.Ignore,
        };
        column.AddThemeConstantOverride("separation", 10);
        column.AddChild(Alert("본진이 공격받고 있습니다", "Space 로 이동 · 3초 전", HudTokens.Seal, display));
        column.AddChild(Alert("공방 일꾼 생산 완료", "관상감 본청 · 대기열 2", HudTokens.Hp, display));
        return column;
    }

    private static Control Alert(string title, string detail, Color accent, Font display)
    {
        var panel = new PanelContainer { MouseFilter = Control.MouseFilterEnum.Ignore };
        StyleBoxFlat box = HudTokens.Box(HudTokens.Wood, accent, 1);
        box.BorderWidthLeft = 4;
        panel.AddThemeStyleboxOverride("panel", box);
        var column = new VBoxContainer();
        panel.AddChild(column);
        column.AddChild(MakeLabel(title, display, 17, HudTokens.Paper));
        column.AddChild(MakeLabel(detail, null, 12, HudTokens.Muted));
        return panel;
    }

    private Control BuildBottomBar(Simulation simulation, WorldView world, Font display)
    {
        var bar = new PanelContainer
        {
            AnchorTop = 1f,
            AnchorRight = 1f,
            AnchorBottom = 1f,
            OffsetTop = -BottomHeight,
            MouseFilter = Control.MouseFilterEnum.Stop,
        };
        bar.AddThemeStyleboxOverride("panel", HudTokens.Box(HudTokens.Wood, HudTokens.Gold, 2, 0));
        var row = new HBoxContainer();
        row.AddThemeConstantOverride("separation", 10);
        bar.AddChild(row);

        Minimap = new Minimap { CustomMinimumSize = new Vector2(172, 172) };
        Minimap.Build(simulation, world);
        row.AddChild(Framed(Minimap));
        row.AddChild(BuildSelectionPanel(display));
        row.AddChild(BuildCommandCard());
        return bar;
    }

    private static Control Framed(Control content)
    {
        var frame = new PanelContainer();
        frame.AddThemeStyleboxOverride("panel", HudTokens.Box(HudTokens.WoodDeep, HudTokens.Gold));
        frame.AddChild(content);
        return frame;
    }

    private Control BuildSelectionPanel(Font display)
    {
        var row = new HBoxContainer { SizeFlagsHorizontal = Control.SizeFlags.ExpandFill };
        row.AddThemeConstantOverride("separation", 12);

        _portrait = new TextureRect
        {
            Texture = _art.Sprite("gunner"),
            CustomMinimumSize = new Vector2(124, 150),
            ExpandMode = TextureRect.ExpandModeEnum.IgnoreSize,
            StretchMode = TextureRect.StretchModeEnum.KeepAspectCentered,
            TextureFilter = CanvasItem.TextureFilterEnum.LinearWithMipmaps,
        };
        row.AddChild(Framed(_portrait));

        var paper = new PanelContainer { CustomMinimumSize = new Vector2(250, 0) };
        paper.AddThemeStyleboxOverride("panel", HudTokens.Box(HudTokens.Paper, HudTokens.Gold, 1));
        var info = new VBoxContainer();
        info.AddThemeConstantOverride("separation", 5);
        paper.AddChild(info);
        _title = MakeLabel("선택 없음", display, 21, HudTokens.Ink);
        info.AddChild(_title);
        info.AddChild(MakeLabel("조선 공방 · 보병 · 원거리", null, 12, new Color("5a4c38")));
        _hpText = MakeLabel("체력   -", null, 12, HudTokens.Ink);
        info.AddChild(_hpText);
        _hpBar = new ProgressBar { ShowPercentage = false, CustomMinimumSize = new Vector2(0, 10) };
        _hpBar.AddThemeStyleboxOverride("background", HudTokens.Box(HudTokens.Ink, HudTokens.Ink, 0, 1));
        _hpBar.AddThemeStyleboxOverride("fill", HudTokens.Box(HudTokens.Hp, HudTokens.Hp, 0, 1));
        info.AddChild(_hpBar);
        _state = MakeLabel("", null, 12, HudTokens.Ink);
        info.AddChild(_state);
        row.AddChild(paper);

        var rosterColumn = new VBoxContainer { SizeFlagsHorizontal = Control.SizeFlags.ExpandFill };
        rosterColumn.AddChild(MakeLabel("부대 Ctrl+2 · 클릭해 단일 선택", null, 12, HudTokens.Muted));
        _roster = new GridContainer { Columns = 6 };
        rosterColumn.AddChild(_roster);
        row.AddChild(rosterColumn);
        return row;
    }

    private static Control BuildCommandCard()
    {
        var grid = new GridContainer { Columns = 4 };
        grid.AddThemeConstantOverride("h_separation", 6);
        grid.AddThemeConstantOverride("v_separation", 6);
        foreach ((string label, string key) in Commands)
        {
            grid.AddChild(CommandSlot(label, key));
        }

        return grid;
    }

    private static Control CommandSlot(string label, string key)
    {
        var slot = new PanelContainer { CustomMinimumSize = new Vector2(96, 58) };
        bool filled = label.Length > 0;
        slot.AddThemeStyleboxOverride("panel", HudTokens.Box(filled ? new Color("3a2a1a") : HudTokens.Slot, filled ? HudTokens.Gold : new Color("3a2c1c"), 1));
        if (filled)
        {
            var column = new VBoxContainer { Alignment = BoxContainer.AlignmentMode.Center };
            Label name = MakeLabel(label, null, 13, HudTokens.Paper);
            name.HorizontalAlignment = HorizontalAlignment.Center;
            Label hotkey = MakeLabel(key, null, 11, HudTokens.GoldBright);
            hotkey.HorizontalAlignment = HorizontalAlignment.Center;
            column.AddChild(hotkey);
            column.AddChild(name);
            slot.AddChild(column);
        }

        return slot;
    }

    private static Label MakeLabel(string text, Font? font, int size, Color color)
    {
        var label = new Label { Text = text, VerticalAlignment = VerticalAlignment.Center, MouseFilter = Control.MouseFilterEnum.Ignore };
        if (font != null)
        {
            label.AddThemeFontOverride("font", font);
        }

        label.AddThemeFontSizeOverride("font_size", size);
        label.AddThemeColorOverride("font_color", color);
        return label;
    }
}

/// <summary>Square map overview. Clicking moves the camera, which also exercises the world-to-screen mapping.</summary>
public partial class Minimap : Control
{
    private Simulation _simulation = null!;
    private WorldView _world = null!;
    private ImageTexture _terrain = null!;

    public event Action<Vector2>? WorldClicked;

    public void Build(Simulation simulation, WorldView world)
    {
        _simulation = simulation;
        _world = world;
        var image = Image.CreateEmpty(simulation.MapWidth, simulation.MapHeight, false, Image.Format.Rgb8);
        ReadOnlySpan<byte> blocked = simulation.Blocked;
        for (int y = 0; y < simulation.MapHeight; y++)
        {
            for (int x = 0; x < simulation.MapWidth; x++)
            {
                image.SetPixel(x, y, blocked[y * simulation.MapWidth + x] != 0 ? new Color("4a4638") : new Color("66753e"));
            }
        }

        _terrain = ImageTexture.CreateFromImage(image);
    }

    public override void _GuiInput(InputEvent @event)
    {
        if (@event is InputEventMouseButton { Pressed: true, ButtonIndex: MouseButton.Left } click)
        {
            WorldClicked?.Invoke(new Vector2(
                click.Position.X / Size.X * _simulation.MapWidth,
                click.Position.Y / Size.Y * _simulation.MapHeight));
            AcceptEvent();
        }
    }

    public override void _Draw()
    {
        DrawTextureRect(_terrain, new Rect2(Vector2.Zero, Size), false);
        float scaleX = Size.X / _simulation.MapWidth;
        float scaleY = Size.Y / _simulation.MapHeight;
        ReadOnlySpan<int> xs = _simulation.UnitXRaw;
        ReadOnlySpan<int> ys = _simulation.UnitYRaw;
        ReadOnlySpan<byte> owners = _simulation.UnitOwner;
        for (int id = 0; id < xs.Length; id++)
        {
            Color color = owners[id] == 0 ? new Color("f2d58a") : new Color("e2573c");
            var position = new Vector2(xs[id] / (float)Fix.OneRaw * scaleX, ys[id] / (float)Fix.OneRaw * scaleY);
            DrawRect(new Rect2(position - Vector2.One, new Vector2(2.5f, 2.5f)), color);
        }
    }
}
