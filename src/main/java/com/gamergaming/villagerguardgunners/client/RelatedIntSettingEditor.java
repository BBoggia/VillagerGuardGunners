package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.config.RelatedIntSetting;
import me.fzzyhmstrs.fzzy_config.entry.EntryValidator.ValidationType;
import me.fzzyhmstrs.fzzy_config.screen.entry.ConfigEntry;
import me.fzzyhmstrs.fzzy_config.screen.widget.LayoutWidget;
import me.fzzyhmstrs.fzzy_config.screen.widget.ValidationBackedNumberFieldWidget;
import net.minecraft.network.chat.Component;
import me.fzzyhmstrs.fzzy_config.util.ValidationResult;
import me.fzzyhmstrs.fzzy_config.validation.misc.ChoiceValidator;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;

/** Uses Fzzy's normal number editor with a live cross-field choice validator. */
public final class RelatedIntSettingEditor {
    private RelatedIntSettingEditor() {}

    public static ConfigEntry.ContentBuilder build(ConfigEntry.ContentBuilder builder,
                                                    ValidatedInt number, RelatedIntSetting setting) {
        var validator = new ChoiceValidator<Integer>(new ChoiceValidator.ValuesPredicate<>(null, null)) {
            @Override public ValidationResult<Integer> validateEntry(Integer input, ValidationType type) {
                return setting.validateEntry(input, type);
            }
        };
        var widget = new ValidationBackedNumberFieldWidget<Integer>(110, 20, setting, validator,
                input -> {
                    if (!Double.isFinite(input) || input != Math.rint(input)
                            || input < Integer.MIN_VALUE || input > Integer.MAX_VALUE)
                        return ValidationResult.Companion.error(setting.get(), Component.translatable(
                                "config.villagerguardgunners.validation.whole_number").getString());
                    return number.validateEntry(input.intValue(), ValidationType.STRONG);
                }, setting::setAndUpdate, true, 0.0);
        // Recheck the relation when the delayed textbox edit applies: its peer
        // may have changed since the user typed a valid number.
        builder.layoutContent(layout -> {
            layout.add("widget", widget,
                    LayoutWidget.Position.Impl.getALIGN_JUSTIFY(), LayoutWidget.Position.Impl.getBELOW());
            return layout;
        });
        return builder;
    }
}
