package net.walksy.client.mixin.botch.commandautofill;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.walksy.client.WalksyClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;

import net.walksy.client.commands.structures.CommandHandler;
import net.walksy.client.modules.chat.CommandAutoFill;
import net.walksy.client.utils.ChatUtils;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.command.CommandSource;

@Mixin(ChatInputSuggestor.class)
public abstract class CommandSuggestorMixin {
    @Shadow TextFieldWidget textField;
    @Shadow CompletableFuture<Suggestions> pendingSuggestions;

    private CommandAutoFill getCommandAutoFill() {
        return (CommandAutoFill) WalksyClient.getInstance().getModules().get("chatsuggestion");
    }

    @Shadow abstract void show(boolean narrateFirstSuggestion);
    
    @Inject(at = @At("TAIL"), method="refresh()V", cancellable = true)
    void onRefresh(CallbackInfo ci) {
        if (!this.getCommandAutoFill().isEnabled()) return;

        String text = this.textField.getText();

        // Make sure that the command starts with a . or ,
        CommandHandler handler = WalksyClient.getInstance().commandHandler;
        if (!text.startsWith(handler.delimiter)) return;

        // We're taking control here!
        ci.cancel();

        // Get the command dispatcher
        String dispatcher = text.substring(0, this.textField.getCursor());
        Integer wordStart = ChatUtils.getStartOfCurrentWord(text);

        // Get the matching suggestions to the current input
        this.pendingSuggestions = CommandSource.suggestMatching(
            this.getCommandAutoFill().getSuggestions(dispatcher, handler),
            new SuggestionsBuilder(text, wordStart)
        );
        
        this.pendingSuggestions.thenRun(() -> {
            if (!this.pendingSuggestions.isDone()) {
                return;
            }
            
            // We cannot use show since it is not a typical command
            this.show(true);
        });
    }
}
