package net.minecraft.server;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ServerFunctionManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier TICK_FUNCTION_TAG = Identifier.withDefaultNamespace("tick");
    private static final Identifier LOAD_FUNCTION_TAG = Identifier.withDefaultNamespace("load");
    private final MinecraftServer server;
    private List<CommandFunction<CommandSourceStack>> ticking = ImmutableList.of();
    private boolean postReload;
    private ServerFunctionLibrary library;

    public ServerFunctionManager(MinecraftServer server, ServerFunctionLibrary library) {
        this.server = server;
        this.library = library;
        this.postReload(library);
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return this.server.getCommands().getDispatcher();
    }

    public void tick() {
        if (this.server.tickRateManager().runsNormally()) {
            if (this.postReload) {
                this.postReload = false;
                Collection<CommandFunction<CommandSourceStack>> tag = this.library.getTag(LOAD_FUNCTION_TAG);
                this.executeTagFunctions(tag, LOAD_FUNCTION_TAG);
            }

            this.executeTagFunctions(this.ticking, TICK_FUNCTION_TAG);
        }
    }

    private void executeTagFunctions(Collection<CommandFunction<CommandSourceStack>> functionObjects, Identifier identifier) {
        Profiler.get().push(identifier::toString);

        for (CommandFunction<CommandSourceStack> commandFunction : functionObjects) {
            this.execute(commandFunction, this.getGameLoopSender());
        }

        Profiler.get().pop();
    }

    public void execute(CommandFunction<CommandSourceStack> function, CommandSourceStack source) {
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push(() -> "function " + function.id());

        try {
            InstantiatedFunction<CommandSourceStack> instantiatedFunction = function.instantiate(null, this.getDispatcher());
            Commands.executeCommandInContext(
                source,
                contextConsumer -> ExecutionContext.queueInitialFunctionCall(contextConsumer, instantiatedFunction, source, CommandResultCallback.EMPTY)
            );
        } catch (FunctionInstantiationException var9) {
        } catch (Exception var10) {
            LOGGER.warn("Failed to execute function {}", function.id(), var10);
        } finally {
            profilerFiller.pop();
        }
    }

    public void replaceLibrary(ServerFunctionLibrary reloader) {
        this.library = reloader;
        this.postReload(reloader);
    }

    private void postReload(ServerFunctionLibrary reloader) {
        this.ticking = List.copyOf(reloader.getTag(TICK_FUNCTION_TAG));
        this.postReload = true;
    }

    public CommandSourceStack getGameLoopSender() {
        return this.server.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER).withSuppressedOutput();
    }

    public Optional<CommandFunction<CommandSourceStack>> get(Identifier function) {
        return this.library.getFunction(function);
    }

    public List<CommandFunction<CommandSourceStack>> getTag(Identifier tag) {
        return this.library.getTag(tag);
    }

    public Iterable<Identifier> getFunctionNames() {
        return this.library.getFunctions().keySet();
    }

    public Iterable<Identifier> getTagNames() {
        return this.library.getAvailableTags();
    }
}
