package com.gxlydlyf.geyserdetector.api;

import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.impl.FormDefinition;
import org.geysermc.cumulus.form.impl.FormDefinitions;
import org.geysermc.cumulus.form.util.FormCodec;
import org.geysermc.cumulus.form.util.FormType;

/**
 * Converts a Cumulus {@link Form} to and from the JSON the plugin channel carries.
 *
 * <p>Cumulus itself is used here rather than a hand-written serializer, so whatever
 * component types Cumulus supports serialize correctly without this class having to
 * track them. The proxy deserializes with the same format, which is why the form can
 * cross a boundary between two different Cumulus versions.
 */
public final class FormSerializer {

    private FormSerializer() {
    }

    /**
     * Serializes a form to JSON.
     *
     * @throws IllegalArgumentException if the form is null or Cumulus does not
     *                                  recognise its type
     */
    @SuppressWarnings("unchecked")
    public static String toJson(Form form) {
        if (form == null) {
            throw new IllegalArgumentException("form cannot be null");
        }
        FormDefinitions definitions = FormDefinitions.instance();
        FormDefinition<Form, ?, ?> definition = definitions.definitionFor(form);
        if (definition == null) {
            throw new IllegalArgumentException(
                    "unsupported form type: " + form.getClass().getName());
        }
        FormCodec<Form, ?> codec = definition.codec();
        return codec.jsonData(form);
    }

    /**
     * Rebuilds a form from JSON.
     *
     * <p>Used on the proxy side. Any payload Cumulus produces is accepted; a
     * malformed or unknown form type surfaces as an exception rather than a
     * half-built form.
     *
     * @throws IllegalArgumentException if the JSON is not a form Cumulus understands
     */
    @SuppressWarnings("unchecked")
    public static Form fromJson(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("form json cannot be empty");
        }
        FormDefinitions definitions = FormDefinitions.instance();
        FormType type = readFormType(json);
        if (type == null) {
            throw new IllegalArgumentException("form json declares no known form type");
        }
        FormCodec<Form, ?> codec = definitions.codecFor(type);
        if (codec == null) {
            throw new IllegalArgumentException("no codec available for " + type);
        }
        try {
            return codec.fromJson(json, (form, raw) -> {
                // Nothing is hooked here: Geyser installs its own raw consumer via
                // FormCache when the form is shown, and that is the one that fires.
                // A consumer passed here would simply never be called.
            });
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("form json did not parse as " + type, e);
        }
    }

    /**
     * Reads the form type discriminator from the JSON.
     *
     * <p>Parsed properly rather than string-matched, because a form's own text
     * content can legitimately contain the word "form".
     */
    private static FormType readFormType(String json) {
        com.google.gson.JsonElement parsed;
        try {
            parsed = com.google.gson.JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("form json is not valid json", e);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("form json must be an object");
        }
        com.google.gson.JsonElement typeField = parsed.getAsJsonObject().get("type");
        if (typeField == null || !typeField.isJsonPrimitive()) {
            return null;
        }
        return switch (typeField.getAsString()) {
            case "form" -> FormType.SIMPLE_FORM;
            case "modal" -> FormType.MODAL_FORM;
            case "custom_form" -> FormType.CUSTOM_FORM;
            default -> null;
        };
    }
}
