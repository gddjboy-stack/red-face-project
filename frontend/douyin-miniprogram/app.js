App({
  globalData: {
    appName: '红颜局中局'
  },
  onLaunch() {
    const env = require('./config/env')
    console.log(`[redface] launch env=${env.currentEnv}`)
  }
})
