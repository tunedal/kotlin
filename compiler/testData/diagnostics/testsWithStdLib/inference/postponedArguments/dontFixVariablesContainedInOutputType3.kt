// FIR_IDENTICAL
// !DIAGNOSTICS: -CAST_NEVER_SUCCEEDS -UNUSED_PARAMETER

val x: Map<String, String> = (null as List<Map<String, String>>).fold(mutableMapOf()) { m, x ->
    val (s, action) = x.entries.first()
    m[s] = action
    m
}