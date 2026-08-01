//! TOML configuration engine — high-performance parse/serialize with comment preservation.
//!
//! Replaces NightConfig's `CommentedFileConfig` on the Rust side.
//! The Java `TomlConfigData` class calls these functions via JNI.
//!
//! ## Design
//!
//! - **Load**: Read TOML file -> parse with `toml_edit` -> serialize to JSON for bulk JNI transfer.
//! - **Save**: Receive JSON from Java -> write back to TOML with `toml_edit` (preserving comments).
//! - **Comments**: Stored alongside values in the JSON payload as `__comment__` keys.
//!
//! ## JNI boundary protocol
//!
//! `loadConfig(path)` -> JSON string: `{"key.subkey": value, "__comment__:key.subkey": "comment text", ...}`
//! `saveConfig(path, json)` -> writes TOML file, returns `true` on success.

use std::collections::BTreeMap;
use std::fs;
use std::path::Path;

use serde_json::{Map, Value};

/// A comment entry in the flattened config map.
const COMMENT_PREFIX: &str = "__comment__:";

/// Load a TOML file and return a flattened JSON map.
///
/// Each top-level and nested key is flattened with dot notation:
/// `[section.subsection] key = "val"` -> `"section.subsection.key": "val"`
///
/// Comments are stored as `"__comment__:section.subsection.key": "comment text"`.
///
/// Returns `None` if the file cannot be read or parsed.
pub fn load_config(path: &str) -> Option<String> {
    let content = fs::read_to_string(path).ok()?;
    parse_toml_to_json(&content)
}

/// Parse TOML text into a flattened JSON string.
pub fn parse_toml_to_json(toml_text: &str) -> Option<String> {
    let doc: toml_edit::DocumentMut = toml_text.parse().ok()?;

    let mut map = Map::new();
    flatten_document(&doc, &mut map);

    serde_json::to_string(&Value::Object(map)).ok()
}

/// Recursively flatten a `toml_edit` document into a dot-notation JSON map.
fn flatten_document(doc: &toml_edit::DocumentMut, map: &mut Map<String, Value>) {
    flatten_table(doc.as_table(), String::new(), map);
}

/// Flatten a TOML table into dot-notation keys.
fn flatten_table(table: &toml_edit::Table, prefix: String, map: &mut Map<String, Value>) {
    // Capture table-level comment (decor prefix on the table itself)
    if !prefix.is_empty() {
        let comment = extract_table_comment(table);
        if !comment.is_empty() {
            map.insert(
                format!("{}{}", COMMENT_PREFIX, prefix),
                Value::String(comment),
            );
        }
    }

    for (key, item) in table.iter() {
        let full_key = if prefix.is_empty() {
            key.to_string()
        } else {
            format!("{}.{}", prefix, key)
        };

        match item {
            toml_edit::Item::Value(value) => {
                // Extract comment: combine key_decor (prefix comment above the key)
                // and value decor (inline suffix comment after the value)
                let comment = extract_value_comment_with_key(table, key, value);
                if !comment.is_empty() {
                    map.insert(
                        format!("{}{}", COMMENT_PREFIX, full_key),
                        Value::String(comment),
                    );
                }

                // Store the value itself
                if let Some(json_val) = toml_value_to_json(value) {
                    map.insert(full_key, json_val);
                }
            }
            toml_edit::Item::Table(sub_table) => {
                flatten_table(sub_table, full_key, map);
            }
            _ => {}
        }
    }
}

/// Extract the comment text associated with a TOML value, combining the key's
/// prefix decor (comment above the key) and the value's suffix decor (inline
/// comment after the value).
fn extract_value_comment_with_key(
    table: &toml_edit::Table,
    key: &str,
    value: &toml_edit::Value,
) -> String {
    let mut comments = Vec::new();

    // key_decor gives the decor BEFORE the key (the comment on the line above)
    // Use `table.key()` + `Key::leaf_decor()` (non-deprecated API)
    if let Some(k) = table.key(key) {
        let kd = k.leaf_decor();
        if let Some(prefix) = kd.prefix() {
            if let Some(text) = prefix.as_str() {
                let text = text.trim();
                if !text.is_empty() {
                    comments.push(text.to_string());
                }
            }
        }
    }

    // Suffix decor (comment after the value on the same line, e.g. `key = val # comment`)
    let suffix = value.decor().suffix();
    if let Some(s) = suffix {
        if let Some(text) = s.as_str() {
            let text = text.trim();
            if !text.is_empty() {
                comments.push(text.to_string());
            }
        }
    }

    if comments.is_empty() {
        String::new()
    } else {
        comments.join("\n")
    }
}

/// Extract the comment text associated with a TOML table header.
fn extract_table_comment(table: &toml_edit::Table) -> String {
    // For explicit tables ([section]), get the decor from the header
    let decor = table.decor();
    let mut comments = Vec::new();

    let prefix = decor.prefix();
    if let Some(p) = prefix {
        if let Some(text) = p.as_str() {
            let text = text.trim();
            if !text.is_empty() {
                comments.push(text.to_string());
            }
        }
    }

    let suffix = decor.suffix();
    if let Some(s) = suffix {
        if let Some(text) = s.as_str() {
            let text = text.trim();
            if !text.is_empty() {
                comments.push(text.to_string());
            }
        }
    }

    if comments.is_empty() {
        String::new()
    } else {
        comments.join("\n")
    }
}

/// Convert a `toml_edit::Value` to a `serde_json::Value`.
fn toml_value_to_json(value: &toml_edit::Value) -> Option<Value> {
    if value.is_str() {
        Some(Value::String(value.as_str().unwrap_or("").to_string()))
    } else if value.is_integer() {
        Some(Value::Number(serde_json::Number::from(
            value.as_integer().unwrap_or(0),
        )))
    } else if value.is_float() {
        let f = value.as_float().unwrap_or(0.0);
        serde_json::Number::from_f64(f).map(Value::Number)
    } else if value.is_bool() {
        Some(Value::Bool(value.as_bool().unwrap_or(false)))
    } else if value.is_array() {
        let arr = value.as_array().unwrap();
        let json_arr: Vec<Value> = arr.iter().filter_map(|v| toml_value_to_json(v)).collect();
        Some(Value::Array(json_arr))
    } else if value.is_inline_table() {
        let tbl = value.as_inline_table().unwrap();
        let mut obj = Map::new();
        for (k, v) in tbl.iter() {
            if let Some(jv) = toml_value_to_json(v) {
                obj.insert(k.to_string(), jv);
            }
        }
        Some(Value::Object(obj))
    } else {
        None
    }
}

/// Save a flattened JSON map back to a TOML file with comments preserved.
///
/// The JSON map contains:
/// - Regular keys: `"section.key": value`
/// - Comment keys: `"__comment__:section.key": "comment text"`
///
/// Returns `true` on success, `false` on error.
pub fn save_config(path: &str, json: &str) -> bool {
    let parsed: Value = match serde_json::from_str(json) {
        Ok(v) => v,
        Err(_) => return false,
    };

    let map = match parsed.as_object() {
        Some(m) => m,
        None => return false,
    };

    let toml_text = build_toml_from_json(map);
    fs::write(path, toml_text).is_ok()
}

/// Build a TOML document string from a flattened JSON map.
fn build_toml_from_json(map: &Map<String, Value>) -> String {
    // Separate values and comments
    let mut values: BTreeMap<String, Value> = BTreeMap::new();
    let mut comments: BTreeMap<String, String> = BTreeMap::new();

    for (key, val) in map {
        if let Some(comment_key) = key.strip_prefix(COMMENT_PREFIX) {
            if let Some(s) = val.as_str() {
                comments.insert(comment_key.to_string(), s.to_string());
            }
        } else {
            values.insert(key.clone(), val.clone());
        }
    }

    // Build a nested structure from dot-notation keys
    let mut root = ConfigNode::new();
    for (key, val) in &values {
        let parts: Vec<&str> = key.split('.').collect();
        let comment = comments.get(key).cloned();
        root.insert(&parts, val.clone(), &comment);
    }

    // Render to TOML text
    root.render_with_path("")
}

/// A node in the configuration tree — either a value or a table with children.
struct ConfigNode {
    /// Child tables and values, ordered by insertion.
    children: Vec<(String, ConfigEntry)>,
}

enum ConfigEntry {
    Value(Value, Option<String>),
    Table(ConfigNode, Option<String>),
}

impl ConfigNode {
    fn new() -> Self {
        ConfigNode {
            children: Vec::new(),
        }
    }

    /// Insert a value at a dot-separated path, creating intermediate tables.
    fn insert(&mut self, parts: &[&str], value: Value, comment: &Option<String>) {
        if parts.is_empty() {
            return;
        }

        if parts.len() == 1 {
            // Leaf value
            let key = parts[0].to_string();
            if let Some(pos) = self.children.iter().position(|(k, _)| k == &key) {
                // Update existing entry
                self.children[pos].1 = ConfigEntry::Value(value, comment.clone());
            } else {
                self.children
                    .push((key, ConfigEntry::Value(value, comment.clone())));
            }
            return;
        }

        // Navigate into or create a sub-table
        let key = parts[0].to_string();
        let remaining = &parts[1..];

        // Find existing table
        let pos = self.children.iter().position(|(k, _)| k == &key);
        match pos {
            Some(idx) => {
                if let ConfigEntry::Table(sub_node, _) = &mut self.children[idx].1 {
                    sub_node.insert(remaining, value, comment);
                }
            }
            None => {
                let mut sub_node = ConfigNode::new();
                sub_node.insert(remaining, value, comment);
                self.children
                    .push((key, ConfigEntry::Table(sub_node, None)));
            }
        }
    }

    /// Render with a full path prefix for nested table headers.
    fn render_with_path(&self, path_prefix: &str) -> String {
        let mut output = String::new();

        // Output leaf values first
        for (key, entry) in &self.children {
            match entry {
                ConfigEntry::Value(val, comment) => {
                    if let Some(c) = comment {
                        if !c.is_empty() {
                            for line in c.lines() {
                                output.push_str(&format!("# {}\n", line));
                            }
                        }
                    }
                    output.push_str(&format!("{} = {}\n", key, json_to_toml_value(val)));
                }
                ConfigEntry::Table(_, _) => {}
            }
        }

        // Then output sub-tables
        for (key, entry) in &self.children {
            if let ConfigEntry::Table(sub_node, comment) = entry {
                if !output.is_empty() && !output.ends_with("\n\n") {
                    output.push('\n');
                }

                if let Some(c) = comment {
                    if !c.is_empty() {
                        for line in c.lines() {
                            output.push_str(&format!("# {}\n", line));
                        }
                    }
                }

                let full_path = if path_prefix.is_empty() {
                    key.clone()
                } else {
                    format!("{}.{}", path_prefix, key)
                };

                output.push_str(&format!("[{}]\n", full_path));
                output.push_str(&sub_node.render_with_path(&full_path));
            }
        }

        output
    }
}

/// Convert a JSON value to its TOML representation string.
fn json_to_toml_value(val: &Value) -> String {
    match val {
        Value::String(s) => {
            // Escape and quote the string
            let escaped = s
                .replace('\\', "\\\\")
                .replace('"', "\\\"")
                .replace('\n', "\\n")
                .replace('\r', "\\r")
                .replace('\t', "\\t");
            format!("\"{}\"", escaped)
        }
        Value::Number(n) => {
            if let Some(i) = n.as_i64() {
                i.to_string()
            } else if let Some(f) = n.as_f64() {
                // Ensure it has a decimal point
                let s = f.to_string();
                if s.contains('.') || s.contains('e') || s.contains('E') {
                    s
                } else {
                    format!("{}.0", s)
                }
            } else {
                n.to_string()
            }
        }
        Value::Bool(b) => b.to_string(),
        Value::Array(arr) => {
            let items: Vec<String> = arr.iter().map(json_to_toml_value).collect();
            format!("[{}]", items.join(", "))
        }
        Value::Object(obj) => {
            // Inline table
            let items: Vec<String> = obj
                .iter()
                .map(|(k, v)| format!("{} = {}", k, json_to_toml_value(v)))
                .collect();
            format!("{{ {} }}", items.join(", "))
        }
        Value::Null => String::from("\"\""),
    }
}

/// Parse a TOML file into a document, apply JSON updates, and write back.
///
/// This is the "merge" approach: load existing TOML, update/add/remove keys from JSON, preserve comments.
/// More efficient for incremental saves where the file already exists with comments.
///
/// Returns `true` on success.
pub fn save_config_merge(path: &str, json: &str) -> bool {
    let parsed: Value = match serde_json::from_str(json) {
        Ok(v) => v,
        Err(_) => return false,
    };

    let map = match parsed.as_object() {
        Some(m) => m,
        None => return false,
    };

    // Load existing document or create a new one
    let existing_content = fs::read_to_string(path).unwrap_or_default();
    let mut doc: toml_edit::DocumentMut = existing_content
        .parse()
        .unwrap_or_else(|_| toml_edit::DocumentMut::new());

    // Separate values and comments
    let mut values: BTreeMap<String, Value> = BTreeMap::new();
    let mut comments: BTreeMap<String, String> = BTreeMap::new();

    for (key, val) in map {
        if let Some(comment_key) = key.strip_prefix(COMMENT_PREFIX) {
            if let Some(s) = val.as_str() {
                comments.insert(comment_key.to_string(), s.to_string());
            }
        } else {
            values.insert(key.clone(), val.clone());
        }
    }

    // Apply values to the document
    for (key, val) in &values {
        set_value_in_document(&mut doc, key, val, comments.get(key));
    }

    // Apply comments for keys that don't have values (e.g., table-level comments)
    for (key, comment) in &comments {
        if !values.contains_key(key) {
            set_comment_in_document(&mut doc, key, comment);
        }
    }

    fs::write(path, doc.to_string()).is_ok()
}

/// Set a value at a dot-separated path in a TOML document.
fn set_value_in_document(
    doc: &mut toml_edit::DocumentMut,
    path: &str,
    value: &Value,
    comment: Option<&String>,
) {
    let parts: Vec<&str> = path.split('.').collect();
    if parts.is_empty() {
        return;
    }

    if parts.len() == 1 {
        let key = parts[0];
        let toml_val = json_to_toml_edit_value(value);
        if let Some(toml_val) = toml_val {
            doc[key] = toml_edit::Item::Value(toml_val);
            if let Some(c) = comment {
                if let Some(item) = doc.get_mut(key) {
                    if let toml_edit::Item::Value(v) = item {
                        v.decor_mut().set_suffix(format!(" # {}", c));
                    }
                }
            }
        } else {
            // Value::Null: remove the key from the document
            doc.remove(key);
        }
        return;
    }

    // Navigate/create nested tables
    let mut current = doc.as_table_mut();
    for (i, part) in parts.iter().enumerate() {
        if i == parts.len() - 1 {
            // Leaf value
            let toml_val = json_to_toml_edit_value(value);
            if let Some(toml_val) = toml_val {
                current[*part] = toml_edit::Item::Value(toml_val);
                if let Some(c) = comment {
                    if let Some(item) = current.get_mut(*part) {
                        if let toml_edit::Item::Value(v) = item {
                            v.decor_mut().set_suffix(format!(" # {}", c));
                        }
                    }
                }
            } else {
                // Value::Null: remove the key from the table
                current.remove(*part);
            }
        } else {
            // Ensure sub-table exists
            if !current.contains_table(*part) {
                current[*part] = toml_edit::Item::Table(toml_edit::Table::new());
            }
            current = match current.get_mut(*part) {
                Some(toml_edit::Item::Table(t)) => t,
                _ => return,
            };
        }
    }
}

/// Set a comment at a dot-separated path in a TOML document.
fn set_comment_in_document(doc: &mut toml_edit::DocumentMut, path: &str, comment: &str) {
    let parts: Vec<&str> = path.split('.').collect();
    if parts.is_empty() {
        return;
    }

    if parts.len() == 1 {
        // Root-level key comment
        if let Some(item) = doc.get_mut(parts[0]) {
            if let toml_edit::Item::Value(v) = item {
                v.decor_mut().set_suffix(format!(" # {}", comment));
            }
        }
        return;
    }

    // Navigate to the table and set its decor
    let mut current = doc.as_table_mut();
    for (i, part) in parts.iter().enumerate() {
        if i == parts.len() - 1 {
            if let Some(toml_edit::Item::Table(t)) = current.get_mut(*part) {
                t.decor_mut().set_prefix(format!("# {}\n", comment));
            }
            return;
        }
        current = match current.get_mut(*part) {
            Some(toml_edit::Item::Table(t)) => t,
            _ => return,
        };
    }
}

/// Convert a JSON value to a `toml_edit::Value`.
fn json_to_toml_edit_value(value: &Value) -> Option<toml_edit::Value> {
    match value {
        Value::String(s) => Some(toml_edit::Value::from(s.clone())),
        Value::Number(n) => {
            if let Some(i) = n.as_i64() {
                Some(toml_edit::Value::from(i))
            } else if let Some(f) = n.as_f64() {
                Some(toml_edit::Value::from(f))
            } else {
                None
            }
        }
        Value::Bool(b) => Some(toml_edit::Value::from(*b)),
        Value::Array(arr) => {
            let mut toml_arr = toml_edit::Array::new();
            for item in arr {
                if let Some(v) = json_to_toml_edit_value(item) {
                    toml_arr.push(v);
                }
            }
            Some(toml_edit::Value::Array(toml_arr))
        }
        Value::Object(obj) => {
            let mut tbl = toml_edit::InlineTable::new();
            for (k, v) in obj {
                if let Some(tv) = json_to_toml_edit_value(v) {
                    tbl.insert(k, tv);
                }
            }
            Some(toml_edit::Value::InlineTable(tbl))
        }
        Value::Null => None,
    }
}

/// Check if a key exists in a TOML file.
///
/// Returns `true` if the key (dot-notation path) exists in the file.
pub fn contains_key(path: &str, key: &str) -> bool {
    let content = match fs::read_to_string(path) {
        Ok(c) => c,
        Err(_) => return false,
    };
    let doc: toml_edit::DocumentMut = match content.parse() {
        Ok(d) => d,
        Err(_) => return false,
    };

    let parts: Vec<&str> = key.split('.').collect();
    let mut current = doc.as_table();

    for (i, part) in parts.iter().enumerate() {
        if i == parts.len() - 1 {
            return current.contains_key(part) && !current.get(part).unwrap().is_table();
        }
        match current.get(part) {
            Some(toml_edit::Item::Table(t)) => current = t,
            _ => return false,
        }
    }
    true
}

/// Get a value from a TOML file as a JSON string.
///
/// Returns `null` if the key doesn't exist.
pub fn get_value(path: &str, key: &str) -> String {
    let content = match fs::read_to_string(path) {
        Ok(c) => c,
        Err(_) => return "null".to_string(),
    };
    let doc: toml_edit::DocumentMut = match content.parse() {
        Ok(d) => d,
        Err(_) => return "null".to_string(),
    };

    let parts: Vec<&str> = key.split('.').collect();
    let mut current = doc.as_table();

    for (i, part) in parts.iter().enumerate() {
        if i == parts.len() - 1 {
            match current.get(part) {
                Some(toml_edit::Item::Value(v)) => {
                    return match toml_value_to_json(v) {
                        Some(jv) => jv.to_string(),
                        None => "null".to_string(),
                    };
                }
                _ => return "null".to_string(),
            }
        } else {
            match current.get(part) {
                Some(toml_edit::Item::Table(t)) => current = t,
                _ => return "null".to_string(),
            }
        }
    }

    "null".to_string()
}

/// Remove a key from a TOML file.
///
/// Returns `true` if the key was removed, `false` if it didn't exist or on error.
pub fn remove_key(path: &str, key: &str) -> bool {
    let content = match fs::read_to_string(path) {
        Ok(c) => c,
        Err(_) => return false,
    };
    let mut doc: toml_edit::DocumentMut = match content.parse() {
        Ok(d) => d,
        Err(_) => return false,
    };

    let parts: Vec<&str> = key.split('.').collect();
    let mut current = doc.as_table_mut();

    for (i, part) in parts.iter().enumerate() {
        if i == parts.len() - 1 {
            if current.contains_key(part) {
                current.remove(part);
                return fs::write(path, doc.to_string()).is_ok();
            } else {
                return false;
            }
        } else {
            match current.get_mut(part) {
                Some(toml_edit::Item::Table(t)) => current = t,
                _ => return false,
            }
        }
    }

    false
}

/// Clear all entries from a TOML file (truncate to empty).
///
/// Returns `true` on success.
pub fn clear_config(path: &str) -> bool {
    fs::write(path, "").is_ok()
}

/// Validate that a file path exists and is readable.
pub fn file_exists(path: &str) -> bool {
    Path::new(path).exists()
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE_TOML: &str = r#"# Top-level comment
[optimizations]
# Enable SIMD acceleration
simd_enabled = true
# Thread count for parallel processing
thread_count = 4

[fixes]
collision_fix = true
# Prevents async teleport crashes
async_teleport_fix = false

[function.tpsbar]
enabled = false
format = "TPS: <tps>"
update_interval = 15
"#;

    #[test]
    fn test_parse_toml_to_json_basic() {
        let json = parse_toml_to_json(SAMPLE_TOML).expect("Failed to parse TOML");

        let parsed: Value = serde_json::from_str(&json).expect("Failed to parse JSON");
        let map = parsed.as_object().expect("Expected JSON object");

        assert_eq!(
            map.get("optimizations.simd_enabled"),
            Some(&Value::Bool(true))
        );
        assert_eq!(
            map.get("optimizations.thread_count"),
            Some(&Value::Number(serde_json::Number::from(4)))
        );
        assert_eq!(map.get("fixes.collision_fix"), Some(&Value::Bool(true)));
        assert_eq!(
            map.get("function.tpsbar.enabled"),
            Some(&Value::Bool(false))
        );
        assert_eq!(
            map.get("function.tpsbar.format"),
            Some(&Value::String("TPS: <tps>".to_string()))
        );
        assert_eq!(
            map.get("function.tpsbar.update_interval"),
            Some(&Value::Number(serde_json::Number::from(15)))
        );
    }

    #[test]
    fn test_parse_preserves_comments() {
        let json = parse_toml_to_json(SAMPLE_TOML).expect("Failed to parse TOML");
        let parsed: Value = serde_json::from_str(&json).expect("Failed to parse JSON");
        let map = parsed.as_object().expect("Expected JSON object");

        let simd_comment = map
            .get(&format!("{}optimizations.simd_enabled", COMMENT_PREFIX))
            .expect("Missing comment for simd_enabled");
        assert!(simd_comment.as_str().unwrap().contains("Enable SIMD"));

        let teleport_comment = map
            .get(&format!("{}fixes.async_teleport_fix", COMMENT_PREFIX))
            .expect("Missing comment for async_teleport_fix");
        assert!(teleport_comment
            .as_str()
            .unwrap()
            .contains("Prevents async teleport"));
    }

    #[test]
    fn test_parse_empty_file() {
        let json = parse_toml_to_json("").expect("Failed to parse empty TOML");
        let parsed: Value = serde_json::from_str(&json).expect("Failed to parse JSON");
        assert!(parsed.as_object().unwrap().is_empty());
    }

    #[test]
    fn test_parse_invalid_toml() {
        let result = parse_toml_to_json("this is not = = valid toml [[");
        assert!(result.is_none());
    }

    #[test]
    fn test_save_and_reload_roundtrip() {
        let tmp = std::env::temp_dir().join("mili_config_test_roundtrip.toml");

        // Build a JSON map
        let mut map = Map::new();
        map.insert("section.enabled".to_string(), Value::Bool(true));
        map.insert(
            "section.count".to_string(),
            Value::Number(serde_json::Number::from(42)),
        );
        map.insert(
            "section.name".to_string(),
            Value::String("test".to_string()),
        );
        map.insert(
            format!("{}section.enabled", COMMENT_PREFIX),
            Value::String("Enable feature".to_string()),
        );
        map.insert(
            "section.numbers".to_string(),
            Value::Array(vec![
                Value::Number(serde_json::Number::from(1)),
                Value::Number(serde_json::Number::from(2)),
                Value::Number(serde_json::Number::from(3)),
            ]),
        );

        let json = serde_json::to_string(&Value::Object(map)).unwrap();
        assert!(save_config(tmp.to_str().unwrap(), &json));

        // Reload and verify
        let reloaded = load_config(tmp.to_str().unwrap()).expect("Failed to reload");
        let parsed: Value = serde_json::from_str(&reloaded).expect("Failed to parse reloaded");

        let m = parsed.as_object().unwrap();
        assert_eq!(m.get("section.enabled"), Some(&Value::Bool(true)));
        assert_eq!(
            m.get("section.count"),
            Some(&Value::Number(serde_json::Number::from(42)))
        );
        assert_eq!(
            m.get("section.name"),
            Some(&Value::String("test".to_string()))
        );

        // Clean up
        let _ = std::fs::remove_file(&tmp);
    }

    #[test]
    fn test_save_config_merge_preserves_comments() {
        let tmp = std::env::temp_dir().join("mili_config_test_merge.toml");

        // Write initial file with comments
        let initial = r#"# Section comment
[server]
# Port number
port = 25565
# Server name
name = "Mili"
"#;
        std::fs::write(&tmp, initial).unwrap();

        // Merge: update port, add new key, keep comments
        let mut map = Map::new();
        map.insert(
            "server.port".to_string(),
            Value::Number(serde_json::Number::from(19132)),
        );
        map.insert(
            "server.max_players".to_string(),
            Value::Number(serde_json::Number::from(100)),
        );
        let json = serde_json::to_string(&Value::Object(map)).unwrap();

        assert!(save_config_merge(tmp.to_str().unwrap(), &json));

        // Read back and verify comments are preserved
        let content = std::fs::read_to_string(&tmp).unwrap();
        assert!(
            content.contains("# Port number"),
            "Comment should be preserved"
        );
        assert!(content.contains("port = 19132"), "Value should be updated");
        assert!(
            content.contains("max_players = 100"),
            "New key should be added"
        );

        // Clean up
        let _ = std::fs::remove_file(&tmp);
    }

    #[test]
    fn test_contains_key() {
        let tmp = std::env::temp_dir().join("mili_config_test_contains.toml");
        std::fs::write(&tmp, SAMPLE_TOML).unwrap();

        assert!(contains_key(
            tmp.to_str().unwrap(),
            "optimizations.simd_enabled"
        ));
        assert!(contains_key(
            tmp.to_str().unwrap(),
            "function.tpsbar.format"
        ));
        assert!(!contains_key(tmp.to_str().unwrap(), "nonexistent.key"));

        let _ = std::fs::remove_file(&tmp);
    }

    #[test]
    fn test_get_value() {
        let tmp = std::env::temp_dir().join("mili_config_test_getval.toml");
        std::fs::write(&tmp, SAMPLE_TOML).unwrap();

        let val = get_value(tmp.to_str().unwrap(), "optimizations.thread_count");
        assert_eq!(val, "4");

        let val = get_value(tmp.to_str().unwrap(), "function.tpsbar.format");
        assert_eq!(val, "\"TPS: <tps>\"");

        let val = get_value(tmp.to_str().unwrap(), "nonexistent.key");
        assert_eq!(val, "null");

        let _ = std::fs::remove_file(&tmp);
    }

    #[test]
    fn test_remove_key() {
        let tmp = std::env::temp_dir().join("mili_config_test_remove.toml");
        std::fs::write(&tmp, SAMPLE_TOML).unwrap();

        assert!(remove_key(
            tmp.to_str().unwrap(),
            "optimizations.simd_enabled"
        ));
        assert!(!contains_key(
            tmp.to_str().unwrap(),
            "optimizations.simd_enabled"
        ));
        assert!(contains_key(
            tmp.to_str().unwrap(),
            "optimizations.thread_count"
        ));

        let _ = std::fs::remove_file(&tmp);
    }

    #[test]
    fn test_clear_config() {
        let tmp = std::env::temp_dir().join("mili_config_test_clear.toml");
        std::fs::write(&tmp, SAMPLE_TOML).unwrap();

        assert!(clear_config(tmp.to_str().unwrap()));
        let content = std::fs::read_to_string(&tmp).unwrap();
        assert!(content.is_empty());

        let _ = std::fs::remove_file(&tmp);
    }

    #[test]
    fn test_json_to_toml_value_string_escaping() {
        let val = Value::String("hello \"world\"\n".to_string());
        let toml_str = json_to_toml_value(&val);
        assert!(toml_str.contains("\\\""));
        assert!(toml_str.contains("\\n"));
    }

    #[test]
    fn test_float_values() {
        let toml = r#"
[physics]
gravity = 9.81
drag = 0.0
"#;
        let json = parse_toml_to_json(toml).expect("Failed to parse");
        let parsed: Value = serde_json::from_str(&json).expect("Failed to parse JSON");
        let map = parsed.as_object().unwrap();

        let gravity = map.get("physics.gravity").unwrap();
        assert!((gravity.as_f64().unwrap() - 9.81).abs() < 0.001);

        let drag = map.get("physics.drag").unwrap();
        assert_eq!(drag.as_f64().unwrap(), 0.0);
    }

    #[test]
    fn test_nested_tables_three_levels() {
        let toml = r#"
[a.b.c]
value = 42
"#;
        let json = parse_toml_to_json(toml).expect("Failed to parse");
        let parsed: Value = serde_json::from_str(&json).expect("Failed to parse JSON");
        let map = parsed.as_object().unwrap();

        assert_eq!(
            map.get("a.b.c.value"),
            Some(&Value::Number(serde_json::Number::from(42)))
        );
    }

    #[test]
    fn test_array_of_strings() {
        let toml = r#"
[colors]
list = ["red", "green", "blue"]
"#;
        let json = parse_toml_to_json(toml).expect("Failed to parse");
        let parsed: Value = serde_json::from_str(&json).expect("Failed to parse JSON");
        let map = parsed.as_object().unwrap();

        let arr = map.get("colors.list").unwrap().as_array().unwrap();
        assert_eq!(arr.len(), 3);
        assert_eq!(arr[0], Value::String("red".to_string()));
        assert_eq!(arr[2], Value::String("blue".to_string()));
    }
}
