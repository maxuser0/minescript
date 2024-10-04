## `blocks_to_image v1`

Create a PNG image file and depth map from the top-down view of a volume of blocks.

&nbsp;

**Requirements**

  Minescript v3.1 or higher
  [lib_blockpack_parser](https://minescript.net/sdm_downloads/lib_blockpack_parser) v1 or higher
  pypng (run: `pip install pypng` or `pip3 install pypng`)

&nbsp;

**Usage**

```
\blocks_to_image X1 Y1 Z1 X2 Y2 Z2 LABEL [dscale=DSCALE]
```

&nbsp;

Generates a 2D image in X and Z corresponding to the
top-down view of the volume of blocks from (X1, Y1, Z1) to (X2,
Y2, Z2). The generated image is named `<LABEL>.png`, along
with a depth map at `<LABEL>-depth.png` and a metadata file
`<LABEL>.json` containing a palette that reflects the mapping
between RGB color values and block types.

&nbsp;

If dscale is provided, depth values in Y are scaled by `<DSCALE>`.

&nbsp;

*Note: Unique colors are chosen randomly for each block type. In a future version, colors should be chosen to resemble the block color.*
