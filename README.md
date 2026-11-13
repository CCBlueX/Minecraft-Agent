# Minecraft Agent

A Kotlin library for Minecraft 26.3 bots: pathfinding, navigation and game skills. Every public API is callable from
Java.

## Libraries

`net.ccbluex:minecraft-agent-<library>`. Each library brings the ones it builds on.

- `common`: everything that runs on every game version
  - `simulation`: player movement, tick by tick
  - `pathfinder`: route searches

## Agent usage

Most of this code was written by AI models. Each declaration they wrote names its model in an `@agent` KDoc tag.
These are all models used so far and what they cost, summed up by [agent-usage](https://github.com/1zun4/agent-usage):

<a href="https://ccbluex.github.io/Minecraft-Agent/ai-usage/">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://ccbluex.github.io/Minecraft-Agent/ai-usage-dark.svg">
    <img alt="AI models used in Minecraft Agent and their cost" src="https://ccbluex.github.io/Minecraft-Agent/ai-usage-light.svg">
  </picture>
</a>

## Commits

`type(scope): subject`, lowercase, no trailing period.

`feat` `fix` `refactor` `chore` `docs`

## License

This project is subject to the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html) (GPLv3). This only applies to source code located directly in this clean repository. During the development and compilation process, additional source code may be used to which we have obtained no rights. Such code is not covered by the GPL license.

For those who are unfamiliar with the license, here is a summary of its main points. This is by no means legal advice nor legally binding.

*Actions that you are allowed to do:*

* Use
* Share
* Modify

*If you do decide to use ANY code from the source:*

* **You must disclose the source code of your modified work and the source code you took from this project. This means you are not allowed to use code from this project (even partially) in a closed-source (or even obfuscated) application.**
* **Your modified application must also be licensed under the GPL.**
