package gfh.mixin;

import net.devtech.grossfabrichacks.relaunch.Relauncher;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    public void addRelaunchButton(CallbackInfo ci) {
        this.addButton(new ButtonWidget(this.width / 2 - 50, this.height / 4 + 24, 100, 20, new LiteralText("relaunch"), (ButtonWidget button) -> Relauncher.standard().debug(true).relaunch()));
    }
}
