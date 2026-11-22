package com.gamergaming.villagerguardgunners.config;

import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.UnaryOperator;
import me.fzzyhmstrs.fzzy_config.entry.EntryCreator;
import me.fzzyhmstrs.fzzy_config.entry.EntryValidator.ValidationType;
import me.fzzyhmstrs.fzzy_config.screen.entry.ConfigEntry;
import me.fzzyhmstrs.fzzy_config.util.ValidationResult;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedMapped;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedNumber.WidgetType;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Cross-field edit validation without making file/sync loading depend on field order. */
public final class RelatedIntSetting extends ValidatedMapped<Integer, Integer> {
    private final ValidatedInt number;
    private final IntPredicate allowed;
    private final String errorKey;

    public RelatedIntSetting(int value, int maximum, int minimum, IntPredicate allowed, String errorKey) {
        this(new ValidatedInt(value, maximum, minimum,
                WidgetType.TEXTBOX), allowed, errorKey);
    }

    private RelatedIntSetting(ValidatedInt number, IntPredicate allowed, String errorKey) {
        super(number, Function.identity(), Function.identity());
        this.number = number;
        this.allowed = allowed;
        this.errorKey = errorKey;
    }

    @Override public ValidationResult<Integer> validateEntry(Integer input, ValidationType type) {
        var result = number.validateEntry(input, type);
        if (!result.isValid() || type == ValidationType.WEAK || allowed.test(input)) return result;
        return ValidationResult.Companion.error(input, Component.translatable(errorKey).getString());
    }

    @Override public ValidationResult<Integer> correctEntry(Integer input, ValidationType type) {
        var bounded = number.correctEntry(input, type);
        var result = validateEntry(bounded.get(), type);
        return result.isValid() ? bounded : ValidationResult.Companion.error(get(), Component.translatable(errorKey).getString());
    }

    @Override public boolean isValidEntry(Object input) {
        return input instanceof Integer value && validateEntry(value, ValidationType.STRONG).isValid();
    }

    @Override public void accept(Integer input) {
        // Runtime setup and sequential deserialization keep their existing range-only contract.
        // Context-menu paste/reset in an attached editor must obey the same rule as its widget.
        if (number.getUpdateManager() != null) super.setAndUpdate(input);
        else number.accept(input);
    }

    @Override public RelatedIntSetting instanceEntry() {
        return new RelatedIntSetting(number.instanceEntry(), allowed, errorKey);
    }

    @OnlyIn(Dist.CLIENT)
    @Override protected UnaryOperator<ConfigEntry.ContentBuilder> contentBuilder(EntryCreator.CreatorContext context) {
        return builder -> com.gamergaming.villagerguardgunners.client.RelatedIntSettingEditor.build(builder, number, this);
    }
}
