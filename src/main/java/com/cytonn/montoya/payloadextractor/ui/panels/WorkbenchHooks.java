package com.cytonn.montoya.payloadextractor.ui.panels;

import com.cytonn.montoya.payloadextractor.db.PayloadCollection;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;

/**
 * Callback contract a {@link PayloadFieldComponent} box uses to report user actions back up to
 * its owning {@link WorkbenchPanel}, which is the only place that owns the working field list and
 * knows how to recompose the real request. Every one of these is expected to result in the
 * Modified Request preview being rebuilt via {@code RequestModifier.buildComposedRequest} - the
 * whole point being that these actions are never merely cosmetic.
 */
public interface WorkbenchHooks {

    /** The field's editable value changed (typed directly, picked from the collection dropdown, or written by a generator/AI suggestion/replay). */
    void onValueChanged(ParsedField field, String newValue);

    /** The "X" button was clicked: remove this field from the working list AND from the real composed request. */
    void onRemoveRequested(ParsedField field);

    /** The Duplicate button was clicked: open a pre-filled Add dialog seeded from this field. */
    void onDuplicateRequested(ParsedField field);

    /** Drag-reorder finished: place {@code field} at {@code newIndexWithinGroup} among its container siblings. */
    void onReorderRequested(ParsedField field, int newIndexWithinGroup);

    /** The ▲ button was clicked: swap this field with the previous one in its own reorder group. */
    void onMoveUpRequested(ParsedField field);

    /** The ▼ button was clicked: swap this field with the next one in its own reorder group. */
    void onMoveDownRequested(ParsedField field);

    /** The per-field Play/replay toggle changed. */
    void onPlayToggled(ParsedField field, boolean enabled);

    /** The Generate button was clicked: open the payload generator dialog for this field. */
    void onGenerateRequested(ParsedField field);

    /** The Replay button was clicked: open the replay configuration dialog for this field. */
    void onReplayRequested(ParsedField field);

    /** The ★ button was clicked: toggle favorite on the collection value matching this field's current text (remembering it first if it isn't stored yet). */
    void onFavoriteToggled(ParsedField field);

    /** Whether the field's current value is marked as a favorite in its backing collection. */
    boolean isFavorite(ParsedField field);

    /** The {@link PayloadCollection} that backs this field's editable value dropdown - created on first use, never null. */
    PayloadCollection collectionFor(ParsedField field);

    /** Manual categorization: "tell the extension this value belongs to type X" - reassigns both the field's and its backing collection's category. */
    void onCategoryReassigned(ParsedField field, String newCategory);
}
