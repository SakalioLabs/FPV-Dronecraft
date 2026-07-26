package com.tenicana.dronecraft.client.command;

import com.tenicana.dronecraft.client.sound.MinecraftNativeEventTraceEvidence;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
		ClientCommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess) -> dispatcher.register(
						ClientCommandManager.literal(COMMAND)
								.then(ClientCommandManager
										.literal("export")
										.executes(context -> execute(
												context.getSource()
										)))
				)
		);
	}

	private static int execute(FabricClientCommandSource source) {
		Path output = source.getClient()
				.gameDirectory
				.toPath()
				.resolve(DIAGNOSTIC_DIRECTORY)
				.resolve(
						"native-trace-alc-v1-"
								+ System.currentTimeMillis()
								+ ".json"
				);
		MinecraftNativeEventTraceEvidence.export(
				source.getClient().getSoundManager(),
				output
		).whenComplete((path, error) ->
				source.getClient().execute(
						() -> feedback(source, path, error)
				)
		);
		source.sendFeedback(Component.literal(
				"Native trace/ALC evidence export requested"
		));
		return 1;
	}

	private static void feedback(
			FabricClientCommandSource source,
			Path output,
			Throwable error
	) {
		if (error == null) {
			source.sendFeedback(Component.literal(
					"Native trace/ALC evidence exported: " + output
			));
			return;
		}
		Throwable cause = error instanceof CompletionException
				&& error.getCause() != null
				? error.getCause()
				: error;
		source.sendError(Component.literal(
				"Native trace/ALC evidence export failed: "
						+ cause.getMessage()
		));
	}
}
