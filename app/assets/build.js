// https://github.com/jrburke/r.js/blob/master/build/example.build.js
({
    appDir: "js",
    baseUrl: "./",
    dir: "../../public/js",
    keepBuildDir: false,
    removeCombined: true,
    optimize: "none",
    mainConfigFile: 'js/config.js',
    paths: { "config": "config-prod" },
    modules: [
        {
            name: "libs/require",
            include: ["config"]
        },
        {
            name: "libs/backbone",
            exclude: ["libs/query"]
        },
        {
            name: "libs/foundation",
            exclude: ["libs/query"]
        },
        {
            name: "model/vs",
            exclude: ["libs/backbone"]
        },
        {
            name: "main",
            exclude: ["libs/backbone", "model/vs"]
        }
    ]
})