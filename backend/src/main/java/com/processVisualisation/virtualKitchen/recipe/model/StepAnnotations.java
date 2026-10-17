package com.processVisualisation.virtualKitchen.recipe.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured @ annotations inside a step's description ({@code actionDescription}, still a plain string):
 * <pre>Add @[onions](ingredient:107) and @[marinated chicken](prepared:12:&lt;stepId&gt;), then heat for 5 minutes.</pre>
 * An annotation points at one of the step's own targets; the step's {@code actionOn} stays the single source of
 * truth (quantities and units live there, never in the text):
 * <ul>
 *   <li>{@code ingredient:<ingredientId>} or {@code ingredient:custom:<name>} → an {@code actionOn.ingredients} entry</li>
 *   <li>{@code prepared:<processId>:<stepId>} (or {@code prepared:<processId>}) → an {@code actionOn.processes} entry</li>
 *   <li>{@code step:<stepId>} → an {@code actionOn.steps} entry (an earlier step's result)</li>
 * </ul>
 * The id is the reference; the bracketed label is a snapshot of the name, read when the id can't be resolved.
 * Text without annotations is ordinary text. Only these three kinds are recognised.
 * <p>
 * The frontend twin is features/recipe-tool/model/stepAnnotations.ts; both are tested against
 * src/test/resources/annotations/annotation-cases.json.
 */
public final class StepAnnotations {

    public static final String INGREDIENT = "ingredient";
    public static final String PREPARED = "prepared";
    public static final String STEP = "step";
    public static final String CUSTOM_INGREDIENT_PREFIX = "custom:";

    public record Annotation(String kind, String id, String label) {
        public Annotation withId(String newId) {
            return new Annotation(kind, newId, label);
        }

        public Annotation withLabel(String newLabel) {
            return new Annotation(kind, id, newLabel);
        }
    }

    /** One piece of a description: plain text, or an annotation (exactly one of the two is set). */
    public record Segment(String text, Annotation annotation) {
        public boolean isAnnotation() {
            return annotation != null;
        }
    }

    private static final Pattern ANNOTATION = Pattern.compile(
            "@\\[((?:\\\\.|[^\\\\\\]])*)\\]\\((ingredient|prepared|step):((?:\\\\.|[^\\\\)])*)\\)");
    private static final Pattern ESCAPED = Pattern.compile("\\\\(.)");

    private StepAnnotations() {
    }

    public static List<Segment> parse(String text) {
        List<Segment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) return segments;
        StringBuilder pending = new StringBuilder();
        Matcher matcher = ANNOTATION.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            pending.append(text, cursor, matcher.start());
            String id = unescape(matcher.group(3)).trim();
            String label = unescape(matcher.group(1));
            if (id.isEmpty()) {
                pending.append(label);
            } else {
                if (pending.length() > 0) segments.add(new Segment(pending.toString(), null));
                pending.setLength(0);
                segments.add(new Segment(null, new Annotation(matcher.group(2), id, label)));
            }
            cursor = matcher.end();
        }
        pending.append(text.substring(cursor));
        if (pending.length() > 0) segments.add(new Segment(pending.toString(), null));
        return segments;
    }

    public static String format(Annotation annotation) {
        return "@[" + annotation.label().replaceAll("([\\\\\\]])", "\\\\$1") + "](" + annotation.kind() + ":"
                + annotation.id().replaceAll("([\\\\)])", "\\\\$1") + ")";
    }

    public static String serialize(List<Segment> segments) {
        StringBuilder out = new StringBuilder();
        segments.forEach(segment -> out.append(segment.isAnnotation() ? format(segment.annotation()) : segment.text()));
        return out.toString();
    }

    public static boolean hasAnnotations(String text) {
        return text != null && ANNOTATION.matcher(text).find();
    }

    public static List<Annotation> annotationsOf(String text) {
        return parse(text).stream().filter(Segment::isAnnotation).map(Segment::annotation).toList();
    }

    /** The description as readable prose, each annotation read as its snapshot label. */
    public static String toPlainText(String text) {
        return toPlainText(text, annotation -> null);
    }

    /** The description as readable prose; {@code resolve} gives an annotation's live label (null/blank: the snapshot). */
    public static String toPlainText(String text, Function<Annotation, String> resolve) {
        if (text == null) return "";
        if (!hasAnnotations(text)) return text;
        StringBuilder out = new StringBuilder();
        for (Segment segment : parse(text)) {
            if (!segment.isAnnotation()) {
                out.append(segment.text());
                continue;
            }
            String live = resolve.apply(segment.annotation());
            out.append(live == null || live.isBlank() ? segment.annotation().label() : live.trim());
        }
        return out.toString();
    }

    /**
     * Rewrites annotations: the mapper returns a replacement, {@code null} to turn it back into its plain label, or
     * the same annotation to keep it. Text without annotations is returned unchanged.
     */
    public static String map(String text, Function<Annotation, Annotation> mapper) {
        if (text == null || !hasAnnotations(text)) return text;
        List<Segment> segments = new ArrayList<>();
        for (Segment segment : parse(text)) {
            if (!segment.isAnnotation()) {
                segments.add(segment);
                continue;
            }
            Annotation mapped = mapper.apply(segment.annotation());
            segments.add(mapped == null ? new Segment(segment.annotation().label(), null) : new Segment(null, mapped));
        }
        return serialize(segments);
    }

    /** Points annotations at new ids; the mapper returns the same id to keep one, {@code null} to unwrap it. */
    public static String remapIds(String text, Function<Annotation, String> mapId) {
        return map(text, annotation -> {
            String id = mapId.apply(annotation);
            return id == null ? null : id.equals(annotation.id()) ? annotation : annotation.withId(id);
        });
    }

    /** The annotation id of an {@code actionOn.ingredients} entry: its catalog id, or {@code custom:<name>}. */
    public static String ingredientId(Object ingredientId, Object customIngredientName) {
        String id = ingredientId == null ? "" : String.valueOf(ingredientId).trim();
        if (!"custom".equals(id)) return id;
        String name = customIngredientName instanceof String string ? string.trim() : "";
        return name.isEmpty() ? "" : CUSTOM_INGREDIENT_PREFIX + name;
    }

    /** The annotation id of an {@code actionOn.processes} entry. */
    public static String preparedId(long processId, String stepId) {
        return stepId == null || stepId.isBlank() ? String.valueOf(processId) : processId + ":" + stepId;
    }

    /** Whether an annotation points at the given target (custom ingredient names ignore case). */
    public static boolean sameTarget(Annotation annotation, String kind, String id) {
        if (!annotation.kind().equals(kind) || id == null) return false;
        return INGREDIENT.equals(kind)
                ? annotation.id().toLowerCase(Locale.ROOT).equals(id.toLowerCase(Locale.ROOT))
                : annotation.id().equals(id);
    }

    private static String unescape(String value) {
        return ESCAPED.matcher(value).replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
    }
}
