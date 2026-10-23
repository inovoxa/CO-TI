<template>
  <Dialog :title="dialogTitle" v-model="dialogVisible">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="140px"
      v-loading="formLoading"
    >
      <el-form-item label="Código do modelo" prop="code">
        <el-input v-model="formData.code" placeholder="Digite o código do modelo" />
      </el-form-item>
      <el-form-item label="Nome do modelo" prop="name">
        <el-input v-model="formData.name" placeholder="Digite o nome do modelo" />
      </el-form-item>
      <el-form-item label="Remetente" prop="nickname">
        <el-input v-model="formData.nickname" placeholder="Digite o remetente" />
      </el-form-item>
      <el-form-item label="Conteúdo do modelo" prop="content">
        <el-input type="textarea" v-model="formData.content" placeholder="Digite o conteúdo do modelo" />
      </el-form-item>
      <el-form-item label="Tipo" prop="type">
        <el-select v-model="formData.type" placeholder="Selecione o tipo">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.SYSTEM_NOTIFY_TEMPLATE_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Situação" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio
            v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="dict.value"
            :value="dict.value"
          >
            {{ dict.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="Observações" prop="remark">
        <el-input v-model="formData.remark" placeholder="Digite as observações" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">OK</el-button>
      <el-button @click="dialogVisible = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as NotifyTemplateApi from '@/api/system/notify/template'
import { CommonStatusEnum } from '@/utils/constants'
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const dialogTitle = ref('') // 弹窗的标题
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formType = ref('') // 表单的类型
const formData = ref<NotifyTemplateApi.NotifyTemplateVO>({
  id: undefined,
  name: '',
  nickname: '',
  code: '',
  content: '',
  type: undefined,
  params: '',
  status: CommonStatusEnum.ENABLE,
  remark: ''
})
const formRules = reactive({
  type: [{ required: true, message: 'Informe o tipo de mensagem', trigger: 'change' }],
  status: [{ required: true, message: 'Informe a situação', trigger: 'blur' }],
  code: [{ required: true, message: 'Informe o código do modelo', trigger: 'blur' }],
  name: [{ required: true, message: 'Informe o nome do modelo', trigger: 'blur' }],
  nickname: [{ required: true, message: 'Informe o remetente', trigger: 'blur' }],
  content: [{ required: true, message: 'Informe o conteúdo do modelo', trigger: 'blur' }]
})
const formRef = ref() // 表单 Ref

/** 打开弹窗 */
const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = type
  formType.value = type
  resetForm()
  // 修改时，设置数据
  if (id) {
    formLoading.value = true
    try {
      formData.value = await NotifyTemplateApi.getNotifyTemplate(id)
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  if (!formRef) return
  const valid = await formRef.value.validate()
  if (!valid) return
  formLoading.value = true
  try {
    const data = formData.value as unknown as NotifyTemplateApi.NotifyTemplateVO
    if (formType.value === 'create') {
      await NotifyTemplateApi.createNotifyTemplate(data)
      message.success('Criado')
    } else {
      await NotifyTemplateApi.updateNotifyTemplate(data)
      message.success('Alterações salvas')
    }
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}
/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    name: '',
    nickname: '',
    code: '',
    content: '',
    type: undefined,
    params: '',
    status: CommonStatusEnum.ENABLE,
    remark: ''
  }
  formRef.value?.resetFields()
}
</script>
