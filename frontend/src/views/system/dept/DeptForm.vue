<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="80px"
    >
      <el-form-item label="Departamento superior" prop="parentId">
        <el-tree-select
          v-model="formData.parentId"
          :data="deptTree"
          :props="defaultProps"
          check-strictly
          default-expand-all
          placeholder="Selecione o departamento superior"
          value-key="deptId"
        />
      </el-form-item>
      <el-form-item label="Nome do departamento" prop="name">
        <el-input v-model="formData.name" placeholder="Digite o nome do departamento" />
      </el-form-item>
      <el-form-item label="Ordem" prop="sort">
        <el-input-number v-model="formData.sort" :min="0" controls-position="right" />
      </el-form-item>
      <el-form-item label="Responsável" prop="leaderUserId">
        <UsuarioSelect
          v-model="formData.leaderUserId"
          clearable
          placeholder="Digite o nome do responsável"
        />
      </el-form-item>
      <el-form-item label="Telefone" prop="phone">
        <el-input v-model="formData.phone" maxlength="11" placeholder="Digite o telefone" />
      </el-form-item>
      <el-form-item label="E-mail" prop="email">
        <el-input v-model="formData.email" maxlength="50" placeholder="Digite o e-mail" />
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
    </el-form>
    <template #footer>
      <el-button type="primary" @click="submitForm">OK</el-button>
      <el-button @click="dialogVisible = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { defaultProps, handleTree } from '@/utils/tree'
import * as DeptApi from '@/api/system/dept'
import UsuarioSelect from '@/components/UsuarioSelect/index.vue'
import { CommonStatusEnum } from '@/utils/constants'
import { FormRules } from 'element-plus'

defineOptions({ name: 'SystemDeptForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const dialogTitle = ref('') // 弹窗的标题
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formType = ref('') // 表单的类型：create - 新增；update - 修改
const formData = ref({
  id: undefined,
  title: '',
  parentId: undefined,
  name: undefined,
  sort: undefined,
  leaderUserId: undefined,
  phone: undefined,
  email: undefined,
  status: CommonStatusEnum.ENABLE
})
const formRules = reactive<FormRules>({
  parentId: [{ required: true, message: 'Informe o departamento superior', trigger: 'blur' }],
  name: [{ required: true, message: 'Informe o nome do departamento', trigger: 'blur' }],
  sort: [{ required: true, message: 'Informe a ordem', trigger: 'blur' }],
  email: [{ type: 'email', message: 'E-mail inválido', trigger: ['blur', 'change'] }],
  phone: [{ pattern: /^\+?[0-9][0-9 ()-]{6,22}[0-9]$/, message: 'Celular inválido', trigger: 'blur' }],
  status: [{ required: true, message: 'Informe a situação', trigger: 'blur' }]
})
const formRef = ref() // 表单 Ref
const deptTree = ref() // 树形结构

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
      formData.value = await DeptApi.getDept(id)
    } finally {
      formLoading.value = false
    }
  }
  // 获得部门树
  await getTree()
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
    const data = formData.value as unknown as DeptApi.DeptVO
    if (formType.value === 'create') {
      await DeptApi.createDept(data)
      message.success(t('common.createSuccess'))
    } else {
      await DeptApi.updateDept(data)
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
    title: '',
    parentId: undefined,
    name: undefined,
    sort: undefined,
    leaderUserId: undefined,
    phone: undefined,
    email: undefined,
    status: CommonStatusEnum.ENABLE
  }
  formRef.value?.resetFields()
}

/** 获得部门树 */
const getTree = async () => {
  deptTree.value = []
  const data = await DeptApi.getSimpleDeptList()
  // 无数据（初始化）或能访问顶层部门时才显示"顶级部门"，后端已对受限用户过滤
  if (data.length === 0 || data.some((d) => d.parentId === 0)) {
    const dept: Tree = { id: 0, name: 'Raiz', children: [] }
    dept.children = handleTree(data)
    deptTree.value.push(dept)
  } else {
    deptTree.value = handleTree(data)
  }
}
</script>
