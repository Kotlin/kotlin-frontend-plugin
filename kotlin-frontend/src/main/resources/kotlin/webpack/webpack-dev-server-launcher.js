#!/usr/bin/env node

var path = require('path');
var fs = require('fs');
var webpack = require('webpack');
var WebpackDevServer = require('webpack-dev-server');

// the following will be replaced with inline json
var RunConfig = require('$RunConfig$');

var config = require(RunConfig.webPackConfig);

for (var name in config.entry) {
    if (config.entry.hasOwnProperty(name)) {
        config.entry[name] = [ "webpack-dev-server/client?http://" + RunConfig.host + ":" + RunConfig.port + "/", "webpack/hot/dev-server", config.entry[name] ];
    }
}

if (!config.plugins) {
    config.plugins = [];
}
config.plugins.push(new webpack.HotModuleReplacementPlugin());

if (!config.devServer) {
    config.devServer = {};
}

if (RunConfig.sourceMap) {
    config.module.rules.push({
        enforce: 'pre',
        test: /\.js$/,
        loader: 'source-map-loader'
    });
    config.devtool = "eval-source-map";
}

config.devServer.inline = true;


var devServer = new WebpackDevServer(
    webpack(config),
    {
        publicPath: RunConfig.publicPath,
        contentBase: (RunConfig.contentPath ? RunConfig.contentPath : undefined),
        stats: RunConfig.stats || "errors-only",
        host: RunConfig.host,
        hot: true,
        before: function(app) {
            app.get(RunConfig.shutDownPath, function(req, res) {
                res.json({ shutdown: 'ok' });
                devServer.close();
            });
        },
        proxy: (RunConfig.proxyUrl) ? {
            "**" : {
                target: RunConfig.proxyUrl,
                secure: false,
                bypass: function(req, res, proxyOptions) {
                    if (RunConfig.contentPath) {
                        var file = path.join(RunConfig.contentPath, req.path);
                        if (fs.existsSync(file)) {
                            return req.path;
                        }
                    }
                }
            }
        } : undefined
    }
);
devServer.listen(RunConfig.port, RunConfig.host);
