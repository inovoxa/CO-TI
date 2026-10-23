<template>
  <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px">
    <el-row>
      <el-col :span="12">
        <el-form-item label="Tabela" prop="tableName">
          <el-input v-model="formData.tableName" placeholder="Digite o nome" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="Descrição" prop="tableComment">
          <el-input v-model="formData.tableComment" placeholder="Digite" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item prop="className">
          <template #label>
            <span>
              Nome da entidade
              <el-tooltip
                content="Por padrão o prefixo da tabela é removido. Se houver nomes repetidos, acrescente um prefixo para o MyBatis não acusar alias duplicado."
                placement="top"
              >
                <Icon class="" icon="ep:question-filled" />
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="formData.className" placeholder="Digite" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="Autor" prop="author">
          <el-input v-model="formData.author" placeholder="Digite" />
        </el-form-item>
      </el-col>
      <el-col :span="24">
        <el-form-item label="Observações" prop="remark">
          <el-input v-model="formData.remark" :rows="3" type="textarea" />
        </el-form-item>
      </el-col>
    </el-row>
  </el-form>
</template>
<script lang="ts" setup>
import * as CodegenApi from '@/api/infra/codegen'
import { PropType } from 'vue'

defineOptions({ name: 'InfraCodegenBasicInfoForm' })

const props = defineProps({
  table: {
    type: Object as PropType<Nullable<CodegenApi.CodegenTableSaveReqVO>>,
    default: () => null
  }
})

const formRef = ref()
const formData = ref<CodegenApi.CodegenTableSaveReqVO>(
  CodegenApi.createEmptyCodegenTableSaveReqVO()
)
const rules = reactive({
  tableName: [required],
  tableComment: [required],
  className: [required],
  author: [required]
})

/** 监听 table 属性，复制给 formData 属性 */
watch(
  () => props.table,
  (table) => {
    if (!table) return
    formData.value = table
  },
  {
    deep: true,
    immediate: true
  }
)

defineExpose({
  validate: async () => unref(formRef)?.validate()
})
</script>
