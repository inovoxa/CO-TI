<template>
  <el-form ref="formRef" :model="formData" :rules="rules" label-width="150px">
    <el-row>
      <el-col :span="12">
        <el-form-item label="Modelo" prop="templateType">
          <el-select v-model="formData.templateType">
            <el-option
              v-for="dict in getIntDictOptions(DICT_TYPE.INFRA_CODEGEN_TEMPLATE_TYPE)"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="Frontend" prop="frontType">
          <el-select v-model="formData.frontType">
            <el-option
              v-for="dict in getIntDictOptions(DICT_TYPE.INFRA_CODEGEN_FRONT_TYPE)"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
      </el-col>

      <el-col :span="12">
        <el-form-item label="Destino" prop="scene">
          <el-select v-model="formData.scene">
            <el-option
              v-for="dict in getIntDictOptions(DICT_TYPE.INFRA_CODEGEN_SCENE)"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item>
          <template #label>
            <span>
              Menu superior
              <el-tooltip content="Menu onde a tela fica, por exemplo Sistema" placement="top">
                <Icon icon="ep:question-filled" />
              </el-tooltip>
            </span>
          </template>
          <el-tree-select
            v-model="formData.parentMenuId"
            :data="menus"
            :props="menuTreeProps"
            check-strictly
            node-key="id"
            placeholder="Selecione o menu"
          />
        </el-form-item>
      </el-col>

      <!--      <el-col :span="12">-->
      <!--        <el-form-item prop="packageName">-->
      <!--          <span slot="label">-->
      <!--            生成包路径-->
      <!--            <el-tooltip content="生成在哪个java包下，例如 com.ruoyi.system" placement="top">-->
      <!--              <i class="el-icon-question"></i>-->
      <!--            </el-tooltip>-->
      <!--          </span>-->
      <!--          <el-input v-model="formData.packageName" />-->
      <!--        </el-form-item>-->
      <!--      </el-col>-->

      <el-col :span="12">
        <el-form-item prop="moduleName">
          <template #label>
            <span>
              Módulo
              <el-tooltip
                content="Módulo (primeiro nível), por exemplo system, infra, itsm"
                placement="top"
              >
                <Icon icon="ep:question-filled" />
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="formData.moduleName" />
        </el-form-item>
      </el-col>

      <el-col :span="12">
        <el-form-item prop="businessName">
          <template #label>
            <span>
              Funcionalidade
              <el-tooltip
                content="Funcionalidade (segundo nível), por exemplo user, permission, dict"
                placement="top"
              >
                <Icon icon="ep:question-filled" />
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="formData.businessName" />
        </el-form-item>
      </el-col>

      <!--      <el-col :span="12">-->
      <!--        <el-form-item prop="businessPackage">-->
      <!--          <span slot="label">-->
      <!--            业务包-->
      <!--            <el-tooltip content="业务包，自定义二级目录。例如说，我们希望将 dictType 和 dictData 归类成 dict 业务" placement="top">-->
      <!--              <i class="el-icon-question"></i>-->
      <!--            </el-tooltip>-->
      <!--          </span>-->
      <!--          <el-input v-model="formData.businessPackage" />-->
      <!--        </el-form-item>-->
      <!--      </el-col>-->

      <el-col :span="12">
        <el-form-item prop="className">
          <template #label>
            <span>
              Classe
              <el-tooltip
                content="Nome da classe (inicial maiúscula), por exemplo SysUser, SysMenu"
                placement="top"
              >
                <Icon icon="ep:question-filled" />
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="formData.className" />
        </el-form-item>
      </el-col>

      <el-col :span="12">
        <el-form-item prop="classComment">
          <template #label>
            <span>
              Descrição da classe
              <el-tooltip content="Descrição da classe, por exemplo Usuário" placement="top">
                <Icon icon="ep:question-filled" />
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="formData.classComment" />
        </el-form-item>
      </el-col>

      <el-col v-if="formData.genType === '1'" :span="24">
        <el-form-item prop="genPath">
          <template #label>
            <span>
              Caminho personalizado
              <el-tooltip
                content="Caminho absoluto no disco; vazio gera dentro do projeto atual"
                placement="top"
              >
                <Icon icon="ep:question-filled" />
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="formData.genPath">
            <template #append>
              <el-dropdown>
                <el-button type="primary">
                  Caminhos recentes
                  <i class="el-icon-arrow-down el-icon--right"></i>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="formData.genPath = '/'">
                      Voltar ao caminho padrão
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-input>
        </el-form-item>
      </el-col>
    </el-row>

    <!-- 树表信息 -->
    <el-row v-if="formData.templateType == 2">
      <el-col :span="24">
        <h4 class="form-header">Árvore</h4>
      </el-col>
      <el-col :span="12">
        <el-form-item prop="treeParentColumnId">
          <template #label>
            <span>
              Campo do pai
              <el-tooltip content="Coluna com o pai, por exemplo parent_id" placement="top">
                <Icon icon="ep:question-filled" />
              </el-tooltip>
            </span>
          </template>
          <el-select v-model="formData.treeParentColumnId" placeholder="Selecione">
            <el-option
              v-for="(column, index) in props.columns"
              :key="index"
              :label="column.columnName + ': ' + column.columnComment"
              :value="column.id"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item prop="treeNameColumnId">
          <template #label>
            <span>
              Campo do nome
              <el-tooltip content="Coluna exibida no nó, por exemplo dept_name" placement="top">
                <Icon icon="ep:question-filled" />
              </el-tooltip>
            </span>
          </template>
          <el-select v-model="formData.treeNameColumnId" placeholder="Selecione">
            <el-option
              v-for="(column, index) in props.columns"
              :key="index"
              :label="column.columnName + ': ' + column.columnComment"
              :value="column.id"
            />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <!-- 主表信息 -->
    <el-row v-if="formData.templateType == 15">
      <el-col :span="24">
        <h4 class="form-header">Tabela mestre</h4>
      </el-col>
      <el-col :span="12">
        <el-form-item prop="masterTableId">
          <template #label>
            <span>
              Tabela mestre
              <el-tooltip content="Nome da tabela mestre, por exemplo system_users" placement="top">
                <Icon icon="ep:question-filled" />
              </el-tooltip>
            </span>
          </template>
          <el-select v-model="formData.masterTableId" placeholder="Selecione">
            <el-option
              v-for="(table0, index) in tables"
              :key="index"
              :label="table0.tableName + ': ' + table0.tableComment"
              :value="table0.id"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item prop="subJoinColumnId">
          <template #label>
            <span>
              Campo de ligação
              <el-tooltip content="Coluna da tabela detalhe que aponta para a mestre, por exemplo user_id" placement="top">
                <Icon icon="ep:question-filled" />
              </el-tooltip>
            </span>
          </template>
          <el-select v-model="formData.subJoinColumnId" placeholder="Selecione">
            <el-option
              v-for="(column, index) in props.columns"
              :key="index"
              :label="column.columnName + ': ' + column.columnComment"
              :value="column.id"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item prop="subJoinMany">
          <template #label>
            <span>
              Relação
              <el-tooltip content="Relação entre mestre e detalhe" placement="top">
                <Icon icon="ep:question-filled" />
              </el-tooltip>
            </span>
          </template>
          <el-radio-group v-model="formData.subJoinMany" placeholder="Selecione">
            <el-radio :value="true">Um para muitos</el-radio>
            <el-radio :value="false">Um para um</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-col>
    </el-row>
  </el-form>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { handleTree } from '@/utils/tree'
import * as CodegenApi from '@/api/infra/codegen'
import * as MenuApi from '@/api/system/menu'
import { PropType } from 'vue'

defineOptions({ name: 'InfraCodegenGenerateInfoForm' })

const props = defineProps({
  table: {
    type: Object as PropType<Nullable<CodegenApi.CodegenTableSaveReqVO>>,
    default: () => null
  },
  columns: {
    type: Array as unknown as PropType<CodegenApi.CodegenColumnVO[]>,
    default: () => null
  }
})

const formRef = ref()
const formData = ref<CodegenApi.CodegenTableSaveReqVO>(
  CodegenApi.createEmptyCodegenTableSaveReqVO()
)

const rules = reactive({
  templateType: [required],
  frontType: [required],
  scene: [required],
  moduleName: [required],
  businessName: [required],
  businessPackage: [required],
  className: [required],
  classComment: [required],
  masterTableId: [required],
  subJoinColumnId: [required],
  subJoinMany: [required],
  treeParentColumnId: [required],
  treeNameColumnId: [required]
})

const tables = ref<CodegenApi.CodegenTableVO[]>([]) // 表定义列表
const menus = ref<any[]>([])
const menuTreeProps = {
  label: 'name'
}

watch(
  () => props.table,
  async (table) => {
    if (!table) return
    formData.value = table
    // 加载表列表
    if (table.dataSourceConfigId >= 0) {
      tables.value = await CodegenApi.getCodegenTableList(formData.value.dataSourceConfigId)
    }
  },
  {
    deep: true,
    immediate: true
  }
)

onMounted(async () => {
  try {
    // 加载菜单
    const resp = await MenuApi.getSimpleMenusList()
    menus.value = handleTree(resp)
  } catch {}
})

defineExpose({
  validate: async () => unref(formRef)?.validate()
})
</script>
