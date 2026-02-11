# OTIAC

> OTIAC - O totan int a chitarr

## Quickstart

### Installation

```supercollider
// install the quark
Quarks.install("https://github.com/otiac/otiac")

// recompile
thisProcess.recompile;

// eventually, open the documentation
HelpBrowser.openHelpFor("Classes/OTIAC_GUI");
```

### Start the GUI

The usage is quite straightforward: Just call the GUI and everything should be handled automatically.

```supercollider
// When the patch opens, follow the steps as indicated by the numbers on the buttons.

OTIAC_GUI.new;
```

#### Optional:
 The GUI can be activated passing a json file with your setup, thus immediately loading presets.
```supercollider
OTIAC_GUI.new("~/Desktop/test_setup.json".standardizePath);
```