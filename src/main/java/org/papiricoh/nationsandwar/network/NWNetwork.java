package org.papiricoh.nationsandwar.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.papiricoh.nationsandwar.core.Actions;

/**
 * Payload registration. Client handlers are referenced only inside lambdas, so a dedicated server never loads them.
 */
public final class NWNetwork {
    private static final String VERSION = "1";

    private NWNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);
        r.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> Actions.handle(p, ctx)));
        r.playToClient(ClaimsPayload.TYPE, ClaimsPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> org.papiricoh.nationsandwar.client.ClientClaims.claims(p)));
        r.playToClient(BriefsPayload.TYPE, BriefsPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> org.papiricoh.nationsandwar.client.ClientClaims.briefs(p)));
        r.playToClient(SiegesPayload.TYPE, SiegesPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> org.papiricoh.nationsandwar.client.ClientClaims.sieges(p)));
        r.playToClient(SelfPayload.TYPE, SelfPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> org.papiricoh.nationsandwar.client.ClientClaims.self(p)));
        r.playToClient(ViewPayload.TYPE, ViewPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> org.papiricoh.nationsandwar.client.ClientPolitics.view(p)));
        r.playToClient(NoticePayload.TYPE, NoticePayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> org.papiricoh.nationsandwar.client.ClientPolitics.notice(p)));
        r.playToClient(OpenScreenPayload.TYPE, OpenScreenPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> org.papiricoh.nationsandwar.client.ClientPolitics.open(p)));
    }
}
