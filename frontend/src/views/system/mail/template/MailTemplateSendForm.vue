<template>
  <Dialog v-model="dialogVisible" title="Testar">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="140px"
    >
      <el-form-item label="Conteúdo do modelo" prop="content">
        <Editor :model-value="formData.content" height="150px" readonly />
      </el-form-item>
      <el-form-item label="Para" prop="toMails">
        <el-input-tag
          v-model="formData.toMails"
          placeholder="E-mails dos destinatários, um por linha"
          class="!w-full"
        />
      </el-form-item>
      <el-form-item label="Cc" prop="ccMails">
        <el-input-tag
          v-model="formData.ccMails"
          placeholder="E-mails em cópia, um por linha"
          class="!w-full"
        />
      </el-form-item>
      <el-form-item label="Cco" prop="bccMails">
        <el-input-tag
          v-model="formData.bccMails"
          placeholder="E-mails em cópia oculta, um por linha"
          class="!w-full"
        />
      </el-form-item>
      <el-form-item
        v-for="param in formData.params"
        :key="param"
        :label="'Parâmetro {' + param + '}'"
        :prop="'templateParams.' + param"
      >
        <el-input
          v-model="formData.templateParams[param]"
          :placeholder="'Digite ' + param + ''"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">OK</el-button>
      <el-button @click="dialogVisible = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as MailTemplateApi from '@/api/system/mail/template'

defineOptions({ name: 'SystemMailTemplateSendForm' })

const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formData = ref({
  content: '',
  params: {},
  toMails: [],
  ccMails: [],
  bccMails: [],
  templateCode: '',
  templateParams: new Map()
})
const formRules = reactive({
  templateCode: [{ required: true, message: 'Informe o nº do modelo', trigger: 'blur' }],
  templateParams: {}
})
const formRef = ref() // 表单 Ref

/** 打开弹窗 */
const open = async (id: number) => {
  dialogVisible.value = true
  resetForm()
  // 设置数据
  formLoading.value = true
  try {
    const data = await MailTemplateApi.getMailTemplate(id)
    // 设置动态表单
    formData.value.content = data.content
    formData.value.params = data.params
    formData.value.templateCode = data.code
    formData.value.templateParams = data.params.reduce((obj, item) => {
      obj[item] = '' // 给每个动态属性赋值，避免无法读取
      return obj
    }, {})
    formRules.templateParams = data.params.reduce((obj, item) => {
      obj[item] = { required: true, message: 'Parâmetro ' + item + ' é obrigatório', trigger: 'blur' }
      return obj
    }, {})
  } finally {
    formLoading.value = false
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const submitForm = async () => {
  // 校验表单
  if (!formRef) return
  const valid = await formRef.value.validate()
  if (!valid) return
  // 提交请求
  formLoading.value = true
  try {
    const data = formData.value as MailTemplateApi.MailSendReqVO
    const logId = await MailTemplateApi.sendMail(data)
    if (logId) {
      message.success('Envio solicitado! Acompanhe no log de envio nº ' + logId)
    }
    dialogVisible.value = false
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    content: '',
    params: {},
    toMails: [],
    ccMails: [],
    bccMails: [],
    templateCode: '',
    templateParams: new Map()
  }
  formRules.templateParams = {}
  formRef.value?.resetFields()
}
</script>
