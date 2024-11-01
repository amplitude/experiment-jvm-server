# [1.5.0](https://github.com/amplitude/experiment-jvm-server/compare/1.4.4...1.5.0) (2024-11-01)


### Bug Fixes

* CohortTooLargeException not thrown when in proxy mode ([#36](https://github.com/amplitude/experiment-jvm-server/issues/36)) ([cc10582](https://github.com/amplitude/experiment-jvm-server/commit/cc1058244582da6835f513c4598e5f66af5597d0))
* fix flag push fallback ([#37](https://github.com/amplitude/experiment-jvm-server/issues/37)) ([2cf5b04](https://github.com/amplitude/experiment-jvm-server/commit/2cf5b04584c716b71c79a91d4220075c12939042))
* remove OptIn annotation for cohort syncing ([#35](https://github.com/amplitude/experiment-jvm-server/issues/35)) ([b5e0684](https://github.com/amplitude/experiment-jvm-server/commit/b5e06848e2238fa450721e8c16e4b95d292f13e0))


### Features

* add flag push ([#30](https://github.com/amplitude/experiment-jvm-server/issues/30)) ([27caeb5](https://github.com/amplitude/experiment-jvm-server/commit/27caeb59e47a710e6a5c682c90e8f3240efce881))

## [1.4.4](https://github.com/amplitude/experiment-jvm-server/compare/1.4.3...1.4.4) (2024-10-02)


### Bug Fixes

* bump amplitude java sdk version ([#34](https://github.com/amplitude/experiment-jvm-server/issues/34)) ([70fdd1c](https://github.com/amplitude/experiment-jvm-server/commit/70fdd1cfc595c3189d651323c7fdcec80bfe208f))

## [1.4.3](https://github.com/amplitude/experiment-jvm-server/compare/1.4.2...1.4.3) (2024-09-23)


### Bug Fixes

* add metric for cohort download too large ([#33](https://github.com/amplitude/experiment-jvm-server/issues/33)) ([5a855c9](https://github.com/amplitude/experiment-jvm-server/commit/5a855c904b011b931467a29f48914bd7867f83db))

## [1.4.2](https://github.com/amplitude/experiment-jvm-server/compare/1.4.1...1.4.2) (2024-09-20)


### Bug Fixes

* fix assignment config to use batch when configured ([#32](https://github.com/amplitude/experiment-jvm-server/issues/32)) ([d6d9684](https://github.com/amplitude/experiment-jvm-server/commit/d6d9684332066aaf5345646061651e67a7ea1247))

## [1.4.1](https://github.com/amplitude/experiment-jvm-server/compare/1.4.0...1.4.1) (2024-09-04)


### Bug Fixes

* fix certain metrics that weren't firing ([dbf2994](https://github.com/amplitude/experiment-jvm-server/commit/dbf299481ab5c2d2f985edb0219a427d5b6a35ee))

# [1.4.0](https://github.com/amplitude/experiment-jvm-server/compare/1.3.1...1.4.0) (2024-08-27)


### Bug Fixes

* use get request for remote fetch ([#29](https://github.com/amplitude/experiment-jvm-server/issues/29)) ([6c2fc3a](https://github.com/amplitude/experiment-jvm-server/commit/6c2fc3ab7d152cdbfbcccf8d6dee761b9d3ffe73))


### Features

* local evaluation cohorts support ([#28](https://github.com/amplitude/experiment-jvm-server/issues/28)) ([d2d5213](https://github.com/amplitude/experiment-jvm-server/commit/d2d5213b57f60ed5d032ebcbbd91487ec8ca7fc9))

## [1.3.1](https://github.com/amplitude/experiment-jvm-server/compare/1.3.0...1.3.1) (2024-05-23)


### Bug Fixes

* add http proxy config to remote evaluation client ([#27](https://github.com/amplitude/experiment-jvm-server/issues/27)) ([2b00d36](https://github.com/amplitude/experiment-jvm-server/commit/2b00d3660e28b271e3eeb6f81510e291fa9617bf))

# [1.3.0](https://github.com/amplitude/experiment-jvm-server/compare/1.2.7...1.3.0) (2024-02-29)


### Features

* evaluation v2 ([#18](https://github.com/amplitude/experiment-jvm-server/issues/18)) ([51ab836](https://github.com/amplitude/experiment-jvm-server/commit/51ab836d9ba923969f479c257471c1754365dbb8))

## [1.2.7](https://github.com/amplitude/experiment-jvm-server/compare/1.2.6...1.2.7) (2024-02-07)


### Bug Fixes

* add serverUrl field to AssignmentConfiguration ([#22](https://github.com/amplitude/experiment-jvm-server/issues/22)) ([eb0b251](https://github.com/amplitude/experiment-jvm-server/commit/eb0b25150563ceb1d3649d932887decc3d6ec2c4))

## [1.2.6](https://github.com/amplitude/experiment-jvm-server/compare/1.2.5...1.2.6) (2024-01-31)


### Bug Fixes

* update OkHttp to 4.12.0 ([#19](https://github.com/amplitude/experiment-jvm-server/issues/19)) ([03db9cf](https://github.com/amplitude/experiment-jvm-server/commit/03db9cf8c97141684ae6edc3fa2d8d73bae050fe))

## [1.2.5](https://github.com/amplitude/experiment-jvm-server/compare/1.2.4...1.2.5) (2024-01-29)


### Bug Fixes

* Improve remote evaluation fetch retry logic ([#17](https://github.com/amplitude/experiment-jvm-server/issues/17)) ([5fe439f](https://github.com/amplitude/experiment-jvm-server/commit/5fe439f6cb4fc9b55ace986105efdd707ebcf676))

## [1.2.4](https://github.com/amplitude/experiment-jvm-server/compare/1.2.3...1.2.4) (2023-11-29)


### Bug Fixes

* local evaluation flag config ConcurrentModificationException ([#16](https://github.com/amplitude/experiment-jvm-server/issues/16)) ([7c5353f](https://github.com/amplitude/experiment-jvm-server/commit/7c5353f2c9d24e3ef9a9ec00bdc40b5953f9c1d2))

## [1.2.3](https://github.com/amplitude/experiment-jvm-server/compare/1.2.2...1.2.3) (2023-11-28)


### Bug Fixes

* Flag segment serialization error ([#15](https://github.com/amplitude/experiment-jvm-server/issues/15)) ([8c6b47e](https://github.com/amplitude/experiment-jvm-server/commit/8c6b47e8df9a42d5b4092fa4db05af1588ce473c))

## [1.2.2](https://github.com/amplitude/experiment-jvm-server/compare/1.2.1...1.2.2) (2023-10-10)


### Bug Fixes

* Catch error in flag poller for local evaluation ([#13](https://github.com/amplitude/experiment-jvm-server/issues/13)) ([5084ed2](https://github.com/amplitude/experiment-jvm-server/commit/5084ed287cb1865c48c05ad6d08965bae8eb0bd0))

## [1.2.1](https://github.com/amplitude/experiment-jvm-server/compare/1.2.0...1.2.1) (2023-09-19)


### Bug Fixes

* Do not track empty assignment events ([#12](https://github.com/amplitude/experiment-jvm-server/issues/12)) ([d0aa48e](https://github.com/amplitude/experiment-jvm-server/commit/d0aa48e14da97c887c8ea716d8fe63ea022e5608))

# [1.2.0](https://github.com/amplitude/experiment-jvm-server/compare/1.1.0...1.2.0) (2023-08-29)


### Bug Fixes

* move AssignmentConfiguration into separate file ([774a60d](https://github.com/amplitude/experiment-jvm-server/commit/774a60d49a89765685cd3d09c4ccab1d465305aa))


### Features

* Automatic assignment tracking ([#10](https://github.com/amplitude/experiment-jvm-server/issues/10)) ([1046e59](https://github.com/amplitude/experiment-jvm-server/commit/1046e59864de6b49e4930583ca7e11beb1ffc248))

# [1.1.0](https://github.com/amplitude/experiment-jvm-server/compare/1.0.0...1.1.0) (2023-03-14)


### Bug Fixes

* remove map based flag config storage ([#7](https://github.com/amplitude/experiment-jvm-server/issues/7)) ([48c0628](https://github.com/amplitude/experiment-jvm-server/commit/48c0628d4989bc051e1cc4a5cc26706bd7013ce1))


### Features

* flag dependencies ([#6](https://github.com/amplitude/experiment-jvm-server/issues/6)) ([63d7e46](https://github.com/amplitude/experiment-jvm-server/commit/63d7e463b569eb1c6cf3329be0c6dda0f118be67))

# 1.0.0 (2023-01-27)


### Bug Fixes

* add benchmarking tests, update core version ([80b8723](https://github.com/amplitude/experiment-jvm-server/commit/80b87236d0b053eb8cef084b8ddf4502cc5a8cc6))
* dont return default variant ([556fca5](https://github.com/amplitude/experiment-jvm-server/commit/556fca5f3090320b347883bcffb25844733be433))
* fix publish command ([696f4c3](https://github.com/amplitude/experiment-jvm-server/commit/696f4c38374471708943992a18dd19ecb33276d6))
* fix publishing config ([4b939cd](https://github.com/amplitude/experiment-jvm-server/commit/4b939cd067e35fd1b94eb5c80411e0eccb8c0ee2))
* lint; update publishing ([146f581](https://github.com/amplitude/experiment-jvm-server/commit/146f58146197b55e1a1f6fd737fc9fff7a0b59da))
* only call enable cohort sync once, more logging ([4308835](https://github.com/amplitude/experiment-jvm-server/commit/430883539e431e2db8a29ef1b7722a28e334d52f))
* publishing via release action ([4d7065e](https://github.com/amplitude/experiment-jvm-server/commit/4d7065eb553bcef51fa428d797631f038e2fcd97))
* set java source compatibility to 1.8 ([7f253c9](https://github.com/amplitude/experiment-jvm-server/commit/7f253c9e41ac0dcb42f6788600548f2f58517227))
* update core version ([af779d3](https://github.com/amplitude/experiment-jvm-server/commit/af779d3bb2f76607f53ca6df88b4c17ab408c118))
* update readme ([3880cad](https://github.com/amplitude/experiment-jvm-server/commit/3880cadd21b22476a1dc1cb2ab5cf534d2275168))


### Features

* add local evaluation library header ([#3](https://github.com/amplitude/experiment-jvm-server/issues/3)) ([ade1fb5](https://github.com/amplitude/experiment-jvm-server/commit/ade1fb5758ccbf7e1b45bb3ecdea4346f2dc5840))
* local evaluation cohorts ([#4](https://github.com/amplitude/experiment-jvm-server/issues/4)) ([b9940f1](https://github.com/amplitude/experiment-jvm-server/commit/b9940f15c39bbbcd1f2d3be935e2af08b1abc909))

## [0.0.4](https://github.com/amplitude/experiment-jvm-server/compare/0.0.3...0.0.4) (2022-05-20)


### Bug Fixes

* fix publishing config ([4b939cd](https://github.com/amplitude/experiment-jvm-server/commit/4b939cd067e35fd1b94eb5c80411e0eccb8c0ee2))
* publishing via release action ([4d7065e](https://github.com/amplitude/experiment-jvm-server/commit/4d7065eb553bcef51fa428d797631f038e2fcd97))

## [0.0.3](https://github.com/amplitude/experiment-jvm-server/compare/0.0.2...0.0.3) (2022-05-20)


### Bug Fixes

* fix publish command ([696f4c3](https://github.com/amplitude/experiment-jvm-server/commit/696f4c38374471708943992a18dd19ecb33276d6))

## [0.0.2](https://github.com/amplitude/experiment-jvm-server/compare/0.0.1...0.0.2) (2022-05-19)


### Bug Fixes

* update readme ([3880cad](https://github.com/amplitude/experiment-jvm-server/commit/3880cadd21b22476a1dc1cb2ab5cf534d2275168))
