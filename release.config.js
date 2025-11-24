module.exports = {
    "branches": ["main"],
    "tagFormat": ["${version}"],
    "plugins": [
        ["@semantic-release/commit-analyzer", {
            "preset": "angular",
            "parserOpts": {
                "noteKeywords": ["BREAKING CHANGE", "BREAKING CHANGES", "BREAKING"]
            }
        }],
        ["@semantic-release/release-notes-generator", {
            "preset": "angular",
        }],
        ["@semantic-release/changelog", {
            "changelogFile": "CHANGELOG.md"
        }],
        "@semantic-release/github",
        [
            "@google/semantic-release-replace-plugin",
            {
                "replacements": [
                    {
                        "files": ["build.gradle.kts"],
                        "from": "version = \".*\"",
                        "to": "version = \"${nextRelease.version}\"",
                        "results": [
                            {
                                "file": "build.gradle.kts",
                                "hasChanged": true,
                                "numMatches": 1,
                                "numReplacements": 1
                            }
                        ],
                        "countMatches": true
                    },
                    {
                        "files": ["src/main/kotlin/Experiment.kt"],
                        "from": "internal const val LIBRARY_VERSION = \".*\"",
                        "to": "internal const val LIBRARY_VERSION = \"${nextRelease.version}\"",
                        "results": [
                            {
                                "file": "src/main/kotlin/Experiment.kt",
                                "hasChanged": true,
                                "numMatches": 1,
                                "numReplacements": 1
                            }
                        ],
                        "countMatches": true
                    },
                ]
            }
        ],
        ["@semantic-release/git", {
            "assets": ["build.gradle.kts", "CHANGELOG.md", "src/main/kotlin/Experiment.kt"],
            "message": "chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}"
        }],
        ["@semantic-release/exec", {
            "publishCmd": "./gradlew publishToMavenCentral",
        }],
    ],
}
