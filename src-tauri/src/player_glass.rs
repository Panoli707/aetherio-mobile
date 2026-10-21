use std::fmt::Write;

pub(crate) const MAX_CONTROL_SURFACES: usize = 16;

#[derive(Debug, serde::Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ControlGlassRect {
    pub left: f64,
    pub top: f64,
    pub right: f64,
    pub bottom: f64,
    pub corner_radius: f64,
}

pub(crate) fn shader_source(template: &str) -> String {
    let mut parameters = String::new();
    let mut masks = String::from(
        "float aetherio_controls_mask(vec2 pixel, float expansion) {\n    float mask = 0.0;\n",
    );
    for index in 0..MAX_CONTROL_SURFACES {
        for field in ["left", "top", "right", "bottom", "radius"] {
            let _ = writeln!(parameters,
                "//!PARAM aetherio_control_{index}_{field}\n//!TYPE DYNAMIC float\n//!MINIMUM 0.0\n//!MAXIMUM 99999.0\n0.0\n");
        }
        let _ = writeln!(masks,
            "    if (aetherio_controls_count > {index}.0) {{\n        vec2 lo = vec2(aetherio_control_{index}_left, aetherio_control_{index}_top) - expansion;\n        vec2 hi = vec2(aetherio_control_{index}_right, aetherio_control_{index}_bottom) + expansion;\n        float radius = min(aetherio_control_{index}_radius + expansion, 0.5 * min(hi.x - lo.x, hi.y - lo.y));\n        vec2 q = abs(pixel - 0.5 * (lo + hi)) - 0.5 * (hi - lo) + radius;\n        float distance = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;\n        mask = max(mask, 1.0 - smoothstep(-0.5, 0.5, distance));\n    }}");
    }
    masks.push_str("    return mask;\n}\n");
    template
        .replace("// AETHERIO_CONTROL_PARAMETERS", &parameters)
        .replace("// AETHERIO_CONTROL_MASK", &masks)
}

pub(crate) fn shader_options(
    surfaces: &[ControlGlassRect],
    alpha: f64,
    viewport_width: f64,
    viewport_height: f64,
) -> Result<String, String> {
    if surfaces.len() > MAX_CONTROL_SURFACES || !alpha.is_finite() {
        return Err(String::from(
            "La geometria del vidrio de los controles es invalida.",
        ));
    }
    let mut options = format!(
        ",aetherio_controls_count={},aetherio_controls_alpha={:.3}",
        surfaces.len(),
        alpha.clamp(0.0, 1.0)
    );
    for (index, surface) in surfaces.iter().enumerate() {
        let coordinates = [
            surface.left,
            surface.top,
            surface.right,
            surface.bottom,
            surface.corner_radius,
        ];
        if coordinates.iter().any(|value| !value.is_finite())
            || surface.left < 0.0
            || surface.top < 0.0
            || surface.right <= surface.left
            || surface.bottom <= surface.top
            || surface.right > viewport_width
            || surface.bottom > viewport_height
            || surface.corner_radius < 0.0
        {
            return Err(String::from(
                "La geometria del vidrio de los controles es invalida.",
            ));
        }
        let _ = write!(options,
            ",aetherio_control_{index}_left={:.3},aetherio_control_{index}_top={:.3},aetherio_control_{index}_right={:.3},aetherio_control_{index}_bottom={:.3},aetherio_control_{index}_radius={:.3}",
            surface.left, surface.top, surface.right, surface.bottom, surface.corner_radius.min(256.0));
    }
    Ok(options)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rect() -> ControlGlassRect {
        ControlGlassRect {
            left: 10.0,
            top: 20.0,
            right: 68.0,
            bottom: 78.0,
            corner_radius: 29.0,
        }
    }

    #[test]
    fn generates_matching_masks_for_both_shader_passes() {
        let shader = shader_source(include_str!("../resources/player-controls-blur.glsl"));
        assert!(!shader.contains("// AETHERIO_CONTROL_"));
        assert_eq!(
            shader.matches("//!PARAM aetherio_control_").count(),
            MAX_CONTROL_SURFACES * 5
        );
        assert_eq!(shader.matches("float aetherio_controls_mask(").count(), 2);
    }

    #[test]
    fn serializes_and_resets_control_masks() {
        let options = shader_options(&[rect()], 1.0, 1920.0, 1080.0).unwrap();
        assert!(options.contains("aetherio_controls_count=1"));
        assert!(options.contains("aetherio_control_0_radius=29.000"));
        let cleared = shader_options(&[], 0.0, 1920.0, 1080.0).unwrap();
        assert!(cleared.contains("aetherio_controls_count=0,aetherio_controls_alpha=0.000"));
    }

    #[test]
    fn rejects_invalid_or_excessive_surfaces() {
        let mut invalid = rect();
        invalid.right = f64::NAN;
        assert!(shader_options(&[invalid], 1.0, 1920.0, 1080.0).is_err());
        assert!(shader_options(&[rect()], 1.0, 60.0, 1080.0).is_err());
        let too_many: Vec<_> = (0..=MAX_CONTROL_SURFACES).map(|_| rect()).collect();
        assert!(shader_options(&too_many, 1.0, 1920.0, 1080.0).is_err());
    }
}
