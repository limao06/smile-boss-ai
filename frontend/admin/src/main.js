import { createApp } from 'vue'
import ElAlert from 'element-plus/es/components/alert/index'
import ElButton from 'element-plus/es/components/button/index'
import ElCard from 'element-plus/es/components/card/index'
import ElCollapse, { ElCollapseItem } from 'element-plus/es/components/collapse/index'
import ElDialog from 'element-plus/es/components/dialog/index'
import ElDrawer from 'element-plus/es/components/drawer/index'
import ElForm, { ElFormItem } from 'element-plus/es/components/form/index'
import ElInput from 'element-plus/es/components/input/index'
import ElInputNumber from 'element-plus/es/components/input-number/index'
import ElSelect, { ElOption } from 'element-plus/es/components/select/index'
import ElSteps, { ElStep } from 'element-plus/es/components/steps/index'
import ElTable, { ElTableColumn } from 'element-plus/es/components/table/index'
import ElTag from 'element-plus/es/components/tag/index'
import ElTimeline, { ElTimelineItem } from 'element-plus/es/components/timeline/index'
import 'element-plus/es/components/base/style/css'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/collapse/style/css'
import 'element-plus/es/components/collapse-item/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/drawer/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/input-number/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/step/style/css'
import 'element-plus/es/components/steps/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'
import 'element-plus/es/components/timeline/style/css'
import 'element-plus/es/components/timeline-item/style/css'
import './style.css'
import App from './App.vue'

const application = createApp(App)
const elementComponents = [
  ElAlert,
  ElButton,
  ElCard,
  ElCollapse,
  ElCollapseItem,
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElStep,
  ElSteps,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTimeline,
  ElTimelineItem,
]

elementComponents.forEach((component) => application.component(component.name, component))
application.mount('#app')
