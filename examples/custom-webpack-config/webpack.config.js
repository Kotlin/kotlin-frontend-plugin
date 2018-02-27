var config = require('./build/WebPackHelper.js')
var path = require('path')

module.exports = {
  entry: config.moduleName,
  output: {
    path: path.resolve('./bundle'),
    publicPath: '/build/',
    filename: 'bundle.js'
  },
  resolve: {
    modules: [ path.resolve('js'), path.resolve('..', 'src'), path.resolve('.'), path.resolve('node_modules') ],
    extensions: ['.js', '.css', '.vue']
  },
  module: {
    rules: [
      { test: /\.vue$/, loader: 'vue-loader' },
      { test: /\.css$/, use: [ 'style-loader', 'css-loader' ] }
    ]
  },
  devtool: '#source-map'
};

console.log(module.exports.resolve.modules);


