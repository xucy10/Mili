package net.minecraft.server;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.io.File;
import net.minecraft.server.players.CachedUserNameToIdResolver;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.server.players.UserNameToIdResolver;
import net.minecraft.util.SignatureValidator;
import org.jspecify.annotations.Nullable;

public record Services(
    MinecraftSessionService sessionService,
    ServicesKeySet servicesKeySet,
    GameProfileRepository profileRepository,
    UserNameToIdResolver nameToIdCache,
    ProfileResolver profileResolver
    , @javax.annotation.Nullable PaperServices paper // Paper
) {
    public static final String USERID_CACHE_FILE = "usercache.json";

    // Paper start - add paper configuration files
    public record PaperServices(
        io.papermc.paper.configuration.PaperConfigurations configurations,
        io.papermc.paper.profile.PaperFilledProfileCache filledProfileCache
    ) {}

    public Services(MinecraftSessionService sessionService, ServicesKeySet servicesKeySet, GameProfileRepository profileRepository, UserNameToIdResolver nameToIdCache, ProfileResolver profileResolver) {
        this(sessionService, servicesKeySet, profileRepository, nameToIdCache, profileResolver, null);
    }

    @Override
    public PaperServices paper() {
        return java.util.Objects.requireNonNull(this.paper);
    }
    // Paper end - add paper configuration files

    public static Services create(YggdrasilAuthenticationService authenticationService, File profileRepository, File userCacheFile, joptsimple.OptionSet optionSet) throws Exception { // Paper - add optionset to load paper config files; add userCacheFile parameter
        MinecraftSessionService minecraftSessionService = authenticationService.createMinecraftSessionService();
        GameProfileRepository gameProfileRepository = authenticationService.createProfileRepository();
        UserNameToIdResolver userNameToIdResolver = new CachedUserNameToIdResolver(gameProfileRepository, userCacheFile); // Paper
        // Paper start - load paper config files from cli options
        final java.nio.file.Path legacyConfigPath = ((File) optionSet.valueOf("paper-settings")).toPath();
        final java.nio.file.Path configDirPath = ((File) optionSet.valueOf("paper-settings-directory")).toPath();
        io.papermc.paper.configuration.PaperConfigurations paperConfigurations = io.papermc.paper.configuration.PaperConfigurations.setup(legacyConfigPath, configDirPath, profileRepository.toPath(), (File) optionSet.valueOf("spigot-settings"));
        PaperServices paperServices = new PaperServices(
            paperConfigurations,
            new io.papermc.paper.profile.PaperFilledProfileCache()
        );
        ProfileResolver profileResolver = new ProfileResolver.Cached(minecraftSessionService, userNameToIdResolver, paperServices.filledProfileCache());
        return new Services(minecraftSessionService, authenticationService.getServicesKeySet(), gameProfileRepository, userNameToIdResolver, profileResolver, paperServices);
        // Paper end - load paper config files from cli options
    }

    public @Nullable SignatureValidator profileKeySignatureValidator() {
        return SignatureValidator.from(this.servicesKeySet, ServicesKeyType.PROFILE_KEY);
    }

    public boolean canValidateProfileKeys() {
        return !this.servicesKeySet.keys(ServicesKeyType.PROFILE_KEY).isEmpty();
    }
}
