package vggcompatprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Render-thread capture of the real production client, after the camera has settled. */
final class BarracksDesignPreviewClient {
    private static int view = -1, settled;
    static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || BarracksDesignPreview.view < 0) return;
        mc.options.hideGui = true;
        mc.setScreen(null);
        if (view != BarracksDesignPreview.view) {
            view = BarracksDesignPreview.view;
            settled = 0;
            mc.options.fov().set(BarracksDesignPreview.views.get(view).fov());
        }
        settled++;
    }
    static void render() {
        if (view < 0 || settled < 45 || BarracksDesignPreview.captured == view) return;
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Probe.DIR.resolve(BarracksDesignPreview.views.get(view).name() + ".png"));
            BarracksDesignPreview.captured = view;
        } catch (Exception error) { throw new RuntimeException(error); }
    }
}
