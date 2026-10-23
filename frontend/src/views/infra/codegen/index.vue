<template>
  <doc-alert title="Gerar código (tabela simples)" url="https://doc.iocoder.cn/new-feature/" />
  <doc-alert title="Gerar código (árvore)" url="https://doc.iocoder.cn/new-feature/tree/" />
  <doc-alert title="Gerar código (mestre/detalhe)" url="https://doc.iocoder.cn/new-feature/master-sub/" />
  <doc-alert title="Teste unitário" url="https://doc.iocoder.cn/unit-test/" />

  <!-- 搜索 -->
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="Tabela" prop="tableName">
        <el-input
          v-model="queryParams.tableName"
          class="!w-240px"
          clearable
          placeholder="Digite a tabela"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="Descrição" prop="tableComment">
        <el-input
          v-model="queryParams.tableComment"
          class="!w-240px"
          clearable
          placeholder="Digite a descrição"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="Criado em" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-240px"
          end-placeholder="Data final"
          start-placeholder="Data inicial"
          type="daterange"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery">
          <Icon class="mr-5px" icon="ep:search" />
          Buscar
        </el-button>
        <el-button @click="resetQuery">
          <Icon class="mr-5px" icon="ep:refresh" />
          Limpar
        </el-button>
        <el-button v-hasPermi="['infra:codegen:create']" type="primary" @click="openImportTable()">
          <Icon class="mr-5px" icon="ep:zoom-in" />
          Importar
        </el-button>
        <el-button
          v-hasPermi="['infra:codegen:delete']"
          type="danger"
          :disabled="checkedIds.length === 0"
          @click="handleDeleteBatch"
        >
          <Icon class="mr-5px" icon="ep:delete" />
          Excluir selecionados
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="handleRowCheckboxChange">
      <el-table-column type="selection" width="55" />
      <el-table-column align="center" label="Fonte de dados">
        <template #default="scope">
          {{
            dataSourceConfigList.find((config) => config.id === scope.row.dataSourceConfigId)?.name
          }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="Tabela" prop="tableName" width="200" />
      <el-table-column
        :show-overflow-tooltip="true"
        align="center"
        label="Descrição"
        prop="tableComment"
        width="200"
      />
      <el-table-column align="center" label="Entidade" prop="className" width="200" />
      <el-table-column
        :formatter="dateFormatter"
        align="center"
        label="Criado em"
        prop="createTime"
        width="180"
      />
      <el-table-column
        :formatter="dateFormatter"
        align="center"
        label="Atualizado em"
        prop="createTime"
        width="180"
      />
      <el-table-column align="center" fixed="right" label="Ações" width="300px">
        <template #default="scope">
          <el-button
            v-hasPermi="['infra:codegen:preview']"
            link
            type="primary"
            @click="handlePreview(scope.row)"
          >
            Visualizar
          </el-button>
          <el-button
            v-hasPermi="['infra:codegen:update']"
            link
            type="primary"
            @click="handleUpdate(scope.row.id)"
          >
            Editar
          </el-button>
          <el-button
            v-hasPermi="['infra:codegen:delete']"
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
          >
            Excluir
          </el-button>
          <el-button
            v-hasPermi="['infra:codegen:update']"
            link
            type="primary"
            @click="handleSyncDB(scope.row)"
          >
            Sincronizar
          </el-button>
          <el-button
            v-hasPermi="['infra:codegen:download']"
            link
            type="primary"
            @click="handleGenTable(scope.row)"
          >
            Gerar código
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 弹窗：导入表 -->
  <ImportTable ref="importRef" @success="getList" />
  <!-- 弹窗：预览代码 -->
  <PreviewCode ref="previewRef" />
</template>
<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import download from '@/utils/download'
import * as CodegenApi from '@/api/infra/codegen'
import * as DataSourceConfigApi from '@/api/infra/dataSourceConfig'
import ImportTable from './ImportTable.vue'
import PreviewCode from './PreviewCode.vue'

defineOptions({ name: 'InfraCodegen' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化
const { push } = useRouter() // 路由跳转

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  tableName: undefined,
  tableComment: undefined,
  createTime: []
})
const queryFormRef = ref() // 搜索的表单
const dataSourceConfigList = ref<DataSourceConfigApi.DataSourceConfigVO[]>([]) // 数据源列表

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await CodegenApi.getCodegenTablePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

/** 导入操作 */
const importRef = ref()
const openImportTable = () => {
  importRef.value.open()
}

/** 编辑操作 */
const handleUpdate = (id: number) => {
  push('/codegen/edit?id=' + id)
}

/** 预览操作 */
const previewRef = ref()
const handlePreview = (row: CodegenApi.CodegenTableVO) => {
  previewRef.value.open(row.id)
}

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起删除
    await CodegenApi.deleteCodegenTable(id)
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 批量删除操作 */
const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows: CodegenApi.CodegenTableVO[]) => {
  checkedIds.value = rows.map((row) => row.id)
}

const handleDeleteBatch = async () => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起批量删除
    await CodegenApi.deleteCodegenTableList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 同步操作  */
const handleSyncDB = async (row: CodegenApi.CodegenTableVO) => {
  // 基于 DB 同步
  const tableName = row.tableName
  try {
    await message.confirm('Sincronizar à força a estrutura da tabela "' + tableName + '"?', t('common.reminder'))
    await CodegenApi.syncCodegenFromDB(row.id)
    message.success('Sincronizado')
  } catch {}
}

/** 生成代码操作 */
const handleGenTable = async (row: CodegenApi.CodegenTableVO) => {
  const res = await CodegenApi.downloadCodegen(row.id)
  download.zip(res, 'codegen-' + row.className + '.zip')
}

/** 初始化 **/
onMounted(async () => {
  await getList()
  // 加载数据源列表
  dataSourceConfigList.value = await DataSourceConfigApi.getDataSourceConfigList()
})
</script>
