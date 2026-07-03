<div align="center">
  <img src="./public/image/lophine/lophine3.png" alt="Mili Logo" width="300">
  
  # Mili
  
  Mili is a Lophine-derived runtime that focuses on Folia compatibility, stability and configurable vanilla behavior. It provides fixes and optimizations to make redstone and survival circuits more reliable under Folia's scheduling model. (For full Fabric-based survival circuits, consider using Fabric builds.)

  **English** | [中文](./README.md)
</div>

---

## ✨ Core Features

- Configurable vanilla behavior to tune gameplay mechanics
- Folia-specific bug fixes and compatibility layers
- Support for multiple world formats (linear / b_linear)
- Redstone and survival-circuit compatibility improvements for Folia
- Ongoing utility features and performance-oriented patches

## 📥 Download

### Stable Releases
See the Releases page on the upstream repository for packaged releases.

### Development Builds
To build the latest development artifacts locally:

```bash
# Clone the project
git clone https://github.com/xucy10/Mili.git
cd Mili

# Apply patches and build (example tasks)
./gradlew applyAllPatches
./gradlew createMojmapPaperclipJar
```

After building, generated JARs are available under `mili-server/build/libs`.

## 🔌 API Usage

Add the Mili API as a compile-only dependency in Gradle:

```kotlin
repositories {
  maven {
    url = "https://repo.menthamc.org/repository/maven-public/"
  }
}

dependencies {
  compileOnly("fun.bm.mili:mili-api:$VERSION")
}
```

Or in Maven:

```xml
<repositories>
  <repository>
    <id>menthamc</id>
    <url>https://repo.menthamc.org/repository/maven-public/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>fun.bm.mili</groupId>
    <artifactId>mili-api</artifactId>
    <version>$VERSION</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

## 💬 Community & Support

See the Chinese README for community links (QQ, Discord, Telegram) and official channels.

### Get Help

- Submit issues on the upstream issue tracker
- Discuss on GitHub Discussions or community channels
- Read the documentation under `docs/`

## 🐛 Bug Reports

When reporting bugs, please include:

- Clear description and reproduction steps
- Full server logs and relevant config files
- Environment details (Minecraft version, plugins, JVM args)

## 🤝 Contributing

See `docs/CONTRIBUTING_EN.md` and `docs/CONTRIBUTING.md` for contribution guidelines and patch workflow.

## 🎉 Thanks

Thanks to all contributors and sponsors. If this project helps you, please consider starring the repository.

