<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="80px"
    >
      <el-form-item label="Nome do perfil" prop="name">
        <el-input v-model="formData.name" placeholder="Digite o nome do perfil" />
      </el-form-item>
      <el-form-item label="Código do perfil" prop="code">
        <el-input v-model="formData.code" placeholder="Digite o código do perfil" />
      </el-form-item>
      <el-form-item label="Ordem" prop="sort">
        <el-input v-model="formData.sort" placeholder="Digite a ordem" />
      </el-form-item>
      <el-form-item label="Situação" prop="status">
        <el-select v-model="formData.status" clearable placeholder="Selecione a situação">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Observações" prop="remark">
        <el-input v-model="formData.remark" placeholder="Digite as observações" type="textarea" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">OK</el-button>
      <el-button @click="dialogVisible = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { CommonStatusEnum } from '@/utils/constants'
import * as RoleApi from '@/api/system/role'

defineOptions({ name: 'SystemRoleForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const dialogTitle = ref('') // 弹窗的标题
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formType = ref('') // 表单的类型：create - 新增；update - 修改
const formData = ref({
  id: undefined,
  name: '',
  code: '',
  sort: undefined,
  status: CommonStatusEnum.ENABLE,
  remark: ''
})
const formRules = reactive({
  name: [{ required: true, message: 'Informe o nome do perfil', trigger: 'blur' }],
  code: [{ required: true, message: 'Informe o código do perfil', trigger: 'change' }],
  sort: [{ required: true, message: 'Informe a ordem', trigger: 'change' }],
  status: [{ required: true, message: 'Informe a situação', trigger: 'change' }],
  remark: [{ required: false, message: 'Informe as observações', trigger: 'blur' }]
})
const formRef = ref() // 表单 Ref

/** 打开弹窗 */
const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm()
  // 修改时，设置数据
  if (id) {
    formLoading.value = true
    try {
      formData.value = await RoleApi.getRole(id)
    } finally {
      formLoading.value = false
    }
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    name: '',
    code: '',
    sort: undefined,
    status: CommonStatusEnum.ENABLE,
    remark: ''
  }
  formRef.value?.resetFields()
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  if (!formRef) return
  const valid = await formRef.value.validate()
  if (!valid) return
  // 提交请求
  formLoading.value = true
  try {
    const data = formData.value as unknown as RoleApi.RoleVO
    if (formType.value === 'create') {
      await RoleApi.createRole(data)
      message.success(t('common.createSuccess'))
    } else {
      await RoleApi.updateRole(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
