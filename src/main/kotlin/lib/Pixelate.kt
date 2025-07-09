package lib

import org.openrndr.draw.Filter1to1
import org.openrndr.draw.filterShaderFromCode
import org.openrndr.extra.parameters.Description
import org.openrndr.extra.parameters.DoubleParameter
import kotlin.collections.getValue
import kotlin.collections.setValue

@Description("pixelate")
class Pixelate: Filter1to1(filterShaderFromCode("""
    in vec2 v_texCoord0;
    uniform sampler2D tex0;
    out vec4 o_color;

    uniform float resolution;

    void main() {
        vec2 uv = v_texCoord0 - (mod(v_texCoord0, resolution * 0.5));
        vec4 color = texture(tex0, uv);
        o_color = color;
    }
""".trimIndent(), "pixelate")) {

    @DoubleParameter("resolution", 0.001, 1.0, precision = 3)
    var resolution: Double by parameters

    init {
        resolution = 0.1
    }
}