## `image_to_blocks v1`

Places blocks in creative mode based on the specified PNG image.

&nbsp;

**Requirements**

  Minescript v3.1 or higher
  pypng (run: `pip install pypng` or `pip3 install pypng`)

&nbsp;

**Usage**

```
\image_to_blocks <x> <y> <z> <imagePngFile> \
    [<depthPngFile> [dscale=<depthScale>]] <orientation>

\image_to_blocks <x> <y> <z> <imageSpecJsonFile> \
    [<imagePngFile>] [<depthPngFile> [dscale=<depthScale>]] \
    [<orientation>]
```

&nbsp;

Loads the image at `imagePngFile` and sets blocks for each
pixel in the image. If `depthPngFile` is specified, its width and
height must match those of `imagePngFile`, and must be
greyscale-only format.

&nbsp;

`depthScale` is an optional factor by which to divide
depth-image values. For example, a value of `dscale=25.5`
would map a depth-image value of 255 (where 0 is black and
255 is white) to 10.

&nbsp;

`orientation` must be a comma-delimited string of world
dimensions with optional +/- sign. 2D example: `x,-y` maps
image x (first dimension) to world x and image y (second
dimension) to world -y; 3D example: `x,-z,y` maps image x (first
dimension) to world x, image y (second dimension) to world -z,
and image depth (third dimension) to world y.

&nbsp;

`imageSpecJsonFile` can be used as a convenient way to
package a specification for converting an image to blocks.
The filename must end in ".json" and must contain a JSON
object with optional fields: `orientation`, `color_map`,
`depth_map`, `depth_scale`, and `palette`. The `orientation`
field is formatted like the `orientation` param (see above). The
`color_map` and `depth_map` fields refer to PNG filenames.
The `depth_scale` field is a positive float in the range [0, 255]
for scaling down depth values. The `palette` field must be an
array of JSON objects with fields `min_alpha` (int in range [0,
255]) and `blocks`; `blocks` fields may be RGB values encoded
as strings with a leading `#`, e.g. `#15b215`, and values are
types of blocks, e.g. `green_wool` or
`oak_leaves[persistent=true]`. Params passed to the
`image_to_blocks` command override corresponding entries
in the JSON file.
