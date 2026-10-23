<template>
  <ContentWrap>
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="68px"
    >
      <el-form-item label="Nome do dicionário" prop="dictType">
        <el-select v-model="queryParams.dictType" class="!w-240px" @change="dictChange">
          <el-option
            v-for="item in dictTypeList"
            :key="item.type"
            :label="item.name"
            :value="item.type"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Rótulo" prop="label">
        <el-input
          v-model="queryParams.label"
          placeholder="Digite o rótulo"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="Situação" prop="status">
        <el-select v-model="queryParams.status" placeholder="Situação" clearable class="!w-240px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> Buscar</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> Limpar</el-button>
        <el-button
          type="primary"
          plain
          @click="openForm('create')"
          v-hasPermi="['system:dict:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> Novo
        </el-button>
        <el-button
          type="success"
          plain
          @click="handleExport"
          :loading="exportLoading"
          v-hasPermi="['system:dict:export']"
        >
          <Icon icon="ep:download" class="mr-5px" /> Exportar
        </el-button>
        <el-button
          type="danger"
          plain
          :disabled="checkedIds.length === 0"
          @click="handleDeleteBatch"
          v-hasPermi="['system:dict:delete']"
        >
          <Icon icon="ep:delete" class="mr-5px" /> Excluir selecionados
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="handleRowCheckboxChange">
      <el-table-column type="selection" width="55" />
      <el-table-column label="Nº" align="center" prop="id" />
      <el-table-column label="Rótulo" align="center" prop="label" />
      <el-table-column label="Valor" align="center" prop="value" />
      <el-table-column label="Ordem" align="center" prop="sort" />
      <el-table-column label="Situação" align="center" prop="status">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="Cor" align="center" prop="colorType" />
      <el-table-column label="CSS Class" align="center" prop="cssClass" />
      <el-table-column label="Observações" align="center" prop="remark" show-overflow-tooltip />
      <el-table-column
        label="Criado em"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="Ações" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['system:dict:update']"
          >
            Editar
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['system:dict:delete']"
          >
            Excluir
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 表单弹窗：添加/修改 -->
  <DictDataForm ref="formRef" @success="getList" />
</template>
<script lang="ts" setup>
import { getIntDictOptions, DICT_TYPE } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import download from '@/utils/download'
import * as DictDataApi from '@/api/system/dict/dict.data'
import * as DictTypeApi from '@/api/system/dict/dict.type'
import DictDataForm from './DictDataForm.vue'

defineOptions({ name: 'SystemDictData' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化
const route = useRoute() // 路由

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  label: '',
  status: undefined,
  dictType: route.params.dictType
})
const queryFormRef = ref() // 搜索的表单
const exportLoading = ref(false) // 导出的加载中
const dictTypeList = ref<DictTypeApi.DictTypeVO[]>() // 字典类型的列表

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await DictDataApi.getDictDataPage(queryParams)
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

/** 字典类型更改同时更新列表数据 */
const dictChange = (v) => {
  queryParams.dictType = v
  handleQuery()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

/** 添加/修改操作 */
const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id, queryParams.dictType)
}

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起删除
    await DictDataApi.deleteDictData(id)
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 批量删除按钮操作 */
const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows: DictDataApi.DictDataVO[]) => {
  checkedIds.value = rows.map((row) => row.id!)
}

const handleDeleteBatch = async () => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起批量删除
    await DictDataApi.deleteDictDataList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 导出按钮操作 */
const handleExport = async () => {
  try {
    // 导出的二次确认
    await message.exportConfirm()
    // 发起导出
    exportLoading.value = true
    const data = await DictDataApi.exportDictData(queryParams)
    download.excel(data, 'itens-dicionario.xls')
  } catch {
  } finally {
    exportLoading.value = false
  }
}

/** 初始化 **/
onMounted(async () => {
  await getList()
  // 查询字典（精简)列表
  dictTypeList.value = await DictTypeApi.getSimpleDictTypeList()
})
</script>
