import { createApp } from 'vue'
import ElAlert from 'element-plus/es/components/alert/index'
import ElButton from 'element-plus/es/components/button/index'
import ElCard from 'element-plus/es/components/card/index'
import ElForm, { ElFormItem } from 'element-plus/es/components/form/index'
import ElInput from 'element-plus/es/components/input/index'
import ElProgress from 'element-plus/es/components/progress/index'
import ElTag from 'element-plus/es/components/tag/index'
import 'element-plus/es/components/base/style/css'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/progress/style/css'
import 'element-plus/es/components/tag/style/css'
import './style.css'
import App from './App.vue'

const application = createApp(App)
const elementComponents = [ElAlert, ElButton, ElCard, ElForm, ElFormItem, ElInput, ElProgress, ElTag]

elementComponents.forEach((component) => application.component(component.name, component))
application.mount('#app')
