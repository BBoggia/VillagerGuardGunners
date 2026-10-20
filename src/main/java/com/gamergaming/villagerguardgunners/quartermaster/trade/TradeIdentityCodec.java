package com.gamergaming.villagerguardgunners.quartermaster.trade;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.trading.MerchantOffer;

/** Preserves the private offer identity alongside the vanilla 1.21 offer codec. */
public final class TradeIdentityCodec {
    private TradeIdentityCodec() {}
    public static Codec<MerchantOffer> wrap(Codec<MerchantOffer> vanilla) {
        return new Codec<>() {
            @Override public <T> DataResult<T> encode(MerchantOffer value, DynamicOps<T> ops, T prefix) {
                var identity = ((TradeOfferAccess) value).villagerGuardGunners$getTradeIdentity();
                var encoded = vanilla.encode(value, ops, prefix);
                return identity == null ? encoded : encoded.flatMap(result -> ops.mergeToMap(result,
                        ops.createString(QuartermasterTrades.KEY), NbtOps.INSTANCE.convertTo(ops, identity)));
            }
            @Override public <T> DataResult<Pair<MerchantOffer, T>> decode(DynamicOps<T> ops, T input) {
                return vanilla.decode(ops, input).map(pair -> {
                    ops.get(input, QuartermasterTrades.KEY).result().ifPresent(identity ->
                            ((TradeOfferAccess) pair.getFirst()).villagerGuardGunners$setTradeIdentity(ops.convertTo(NbtOps.INSTANCE, identity)));
                    return pair;
                });
            }
        };
    }
}
