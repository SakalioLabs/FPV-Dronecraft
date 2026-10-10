package com.tenicana.dronecraft.client.command;

import com.tenicana.dronecraft.client.sound.MinecraftNativeEventTraceEvidence;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.minecraft.client.Minecraft;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.concurrent.CompletionException;

/** Dedicated read-only command exposed only by the armed trace mode. */
public final class NativeEventTraceDiagnosticClientCommands {
	private static final String COMMAND =
			"fpvdrone-native-acoustic-trace";
	private static final String DIAGNOSTIC_DIRECTORY =
			"acoustic-diagnostics";

	private NativeEventTraceDiagnosticClientCommands() {
	}

	public static void initialize() {
		NeoForge.EVENT_BUS.addListener(
				(RegisterClientCommandsEvent event) -> event.getDispatcher().register(
						Commands.literal(COMMAND)
								.then(Commands
										.literal("export")
										.executes(context -> execute(
												context.getSource()
										)))
				)
		);
	}

	private static int execute(CommandSourceStack source) {
		Path output = Minecraft.getInstance()
				.gameDirectory
				.toPath()
				.resolve(DIAGNOSTIC_DIRECTORY)
				.resolve(
						"native-trace-alc-v1-"
								+ System.currentTimeMillis()
								+ ".json"
				);
		MinecraftNativeEventTraceEvidence.export(
				Minecraft.getInstance().getSoundManager(),
				output
		).whenComplete((path, error) ->
				Minecraft.getInstance().execute(
						() -> feedback(source, path, error)
				)
		);
		source.sendSystemMessage(Component.literal(
				"Native trace/ALC evidence export requested"
		));
		return 1;
	}

	private static void feedback(
			CommandSourceStack source,
			Path output,
			Throwable error
	) {
		if (error == null) {
			source.sendSystemMessage(Component.literal(
					"Native trace/ALC evidence exported: " + output
			));
			return;
		}
		Throwable cause = error instanceof CompletionException
				&& error.getCause() != null
				? error.getCause()
				: error;
		source.sendFailure(Component.literal(
				"Native trace/ALC evidence export failed: "
						+ cause.getMessage()
		));
	}
}
