<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="130px"
    >
      <el-form-item label="Nome da configuração" prop="name">
        <el-input v-model="formData.name" placeholder="Digite o nome da configuração" />
      </el-form-item>
      <el-form-item label="Observações" prop="remark">
        <el-input v-model="formData.remark" placeholder="Digite as observações" />
      </el-form-item>
      <el-form-item label="Armazenamento" prop="storage">
        <el-select
          v-model="formData.storage"
          :disabled="formData.id !== undefined"
          placeholder="Selecione o armazenamento"
        >
          <el-option
            v-for="dict in getDictOptions(DICT_TYPE.INFRA_FILE_STORAGE)"
            :key="dict.value"
            :label="dict.label"
            :value="parseInt(dict.value)"
          />
        </el-select>
      </el-form-item>
      <!-- DB -->
      <!-- Local / FTP / SFTP -->
      <el-form-item
        v-if="formData.storage >= 10 && formData.storage <= 12"
        label="Caminho base"
        prop="config.basePath"
      >
        <el-input v-model="formData.config.basePath" placeholder="Digite o caminho base" />
      </el-form-item>
      <el-form-item
        v-if="formData.storage >= 11 && formData.storage <= 12"
        label="Servidor"
        prop="config.host"
      >
        <el-input v-model="formData.config.host" placeholder="Digite o servidor" />
      </el-form-item>
      <el-form-item
        v-if="formData.storage >= 11 && formData.storage <= 12"
        label="Porta"
        prop="config.port"
      >
        <el-input-number v-model="formData.config.port" :min="0" placeholder="Digite a porta" />
      </el-form-item>
      <el-form-item
        v-if="formData.storage >= 11 && formData.storage <= 12"
        label="Usuário"
        prop="config.username"
      >
        <el-input v-model="formData.config.username" placeholder="Digite a senha" />
      </el-form-item>
      <el-form-item
        v-if="formData.storage >= 11 && formData.storage <= 12"
        label="Senha"
        prop="config.password"
      >
        <el-input v-model="formData.config.password" placeholder="Digite a senha" />
      </el-form-item>
      <el-form-item v-if="formData.storage === 11" label="Modo de conexão" prop="config.mode">
        <el-radio-group v-model="formData.config.mode">
          <el-radio key="Active" value="Active">Ativo</el-radio>
          <el-radio key="Passive" value="Passive">Passivo</el-radio>
        </el-radio-group>
      </el-form-item>
      <!-- S3 -->
      <el-form-item v-if="formData.storage === 20" label="Endpoint" prop="config.endpoint">
        <el-input v-model="formData.config.endpoint" placeholder="Digite o endpoint" />
      </el-form-item>
      <el-form-item v-if="formData.storage === 20" label="Bucket" prop="config.bucket">
        <el-input v-model="formData.config.bucket" placeholder="Digite o bucket" />
      </el-form-item>
      <el-form-item v-if="formData.storage === 20" label="accessKey" prop="config.accessKey">
        <el-input v-model="formData.config.accessKey" placeholder="Digite a accessKey" />
      </el-form-item>
      <el-form-item v-if="formData.storage === 20" label="accessSecret" prop="config.accessSecret">
        <el-input v-model="formData.config.accessSecret" placeholder="Digite a accessSecret" />
      </el-form-item>
      <el-form-item
        v-if="formData.storage === 20"
        label="Path style"
        prop="config.enablePathStyleAccess"
      >
        <el-radio-group v-model="formData.config.enablePathStyleAccess">
          <el-radio key="true" :value="true">Ativar</el-radio>
          <el-radio key="false" :value="false">Desativado</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        v-if="formData.storage === 20"
        label="Acesso público"
        prop="config.enablePublicAccess"
      >
        <el-radio-group v-model="formData.config.enablePublicAccess">
          <el-radio key="true" :value="true">Público</el-radio>
          <el-radio key="false" :value="false">Privado</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="formData.storage === 20" label="Região">
        <!-- 选填，无需参数校验 -->
        <el-input v-model="formData.config.region" placeholder="Região (em geral só a AWS exige)" />
      </el-form-item>
      <!-- 通用 -->
      <el-form-item v-if="formData.storage === 20" label="Domínio personalizado">
        <!-- 无需参数校验，所以去掉 prop -->
        <el-input v-model="formData.config.domain" placeholder="Digite o domínio personalizado" />
      </el-form-item>
      <el-form-item v-else-if="formData.storage" label="Domínio personalizado" prop="config.domain">
        <el-input v-model="formData.config.domain" placeholder="Digite o domínio personalizado" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">OK</el-button>
      <el-button @click="dialogVisible = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getDictOptions } from '@/utils/dict'
import * as FileConfigApi from '@/api/infra/fileConfig'
import { FormRules } from 'element-plus'

defineOptions({ name: 'InfraFileConfigForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const dialogTitle = ref('') // 弹窗的标题
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formType = ref('') // 表单的类型：create - 新增；update - 修改
const formData = ref({
  id: undefined,
  name: '',
  storage: 0,
  remark: '',
  config: {} as FileConfigApi.FileClientConfig
})
const formRules = reactive<FormRules>({
  name: [{ required: true, message: 'Informe o nome da configuração', trigger: 'blur' }],
  storage: [{ required: true, message: 'Informe o armazenamento', trigger: 'change' }],
  config: {
    basePath: [{ required: true, message: 'Informe o caminho base', trigger: 'blur' }],
    host: [{ required: true, message: 'Informe o servidor', trigger: 'blur' }],
    port: [{ required: true, message: 'Informe a porta', trigger: 'blur' }],
    username: [{ required: true, message: 'Informe o usuário', trigger: 'blur' }],
    password: [{ required: true, message: 'Informe a senha', trigger: 'blur' }],
    mode: [{ required: true, message: 'Informe o modo de conexão', trigger: 'change' }],
    endpoint: [{ required: true, message: 'Informe o endpoint', trigger: 'blur' }],
    bucket: [{ required: true, message: 'Informe o bucket', trigger: 'blur' }],
    accessKey: [{ required: true, message: 'Informe a accessKey', trigger: 'blur' }],
    accessSecret: [{ required: true, message: 'Informe a accessSecret', trigger: 'blur' }],
    enablePathStyleAccess: [
      { required: true, message: 'Informe se usa path style', trigger: 'change' }
    ],
    enablePublicAccess: [{ required: true, message: 'Informe o tipo de acesso', trigger: 'change' }],
    domain: [{ required: true, message: 'Informe o domínio personalizado', trigger: 'blur' }]
  } as FormRules
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
      formData.value = await FileConfigApi.getFileConfig(id)
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
  // 提交请求
  formLoading.value = true
  try {
    const data = formData.value as unknown as FileConfigApi.FileConfigVO
    if (formType.value === 'create') {
      await FileConfigApi.createFileConfig(data)
      message.success(t('common.createSuccess'))
    } else {
      await FileConfigApi.updateFileConfig(data)
      message.success(t('common.updateSuccess'))
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
    storage: undefined!,
    remark: '',
    config: {} as FileConfigApi.FileClientConfig
  }
  formRef.value?.resetFields()
}
</script>
