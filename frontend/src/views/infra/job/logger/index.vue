<template>
  <doc-alert title="Tarefas agendadas" url="https://doc.iocoder.cn/job/" />
  <doc-alert title="Tarefas assíncronas" url="https://doc.iocoder.cn/async-task/" />
  <doc-alert title="Fila de mensagens" url="https://doc.iocoder.cn/message-queue/" />

  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="120px"
    >
      <el-form-item label="Handler" prop="handlerName">
        <el-input
          v-model="queryParams.handlerName"
          placeholder="Digite o handler"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="Início" prop="beginTime">
        <el-date-picker
          v-model="queryParams.beginTime"
          type="date"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="Selecione o início"
          clearable
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="Fim" prop="endTime">
        <el-date-picker
          v-model="queryParams.endTime"
          type="date"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="Selecione o fim"
          clearable
          :default-time="new Date('1 23:59:59')"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="Situação" prop="status">
        <el-select
          v-model="queryParams.status"
          placeholder="Selecione a situação"
          clearable
          class="!w-240px"
        >
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.INFRA_JOB_LOG_STATUS)"
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
          type="success"
          plain
          @click="handleExport"
          :loading="exportLoading"
          v-hasPermi="['infra:job:export']"
        >
          <Icon icon="ep:download" class="mr-5px" /> Exportar
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="Nº" align="center" prop="id" />
      <el-table-column label="Nº" align="center" prop="jobId" />
      <el-table-column label="Handler" align="center" prop="handlerName" />
      <el-table-column label="Parâmetros do handler" align="center" prop="handlerParam" />
      <el-table-column label="Execução nº" align="center" prop="executeIndex" />
      <el-table-column label="Executada em" align="center" width="170s">
        <template #default="scope">
          <span>{{ formatDate(scope.row.beginTime) + ' ~ ' + formatDate(scope.row.endTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="Duração" align="center" prop="duration">
        <template #default="scope">
          <span>{{ scope.row.duration + ' ms' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="Situação" align="center" prop="status">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.INFRA_JOB_LOG_STATUS" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="Ações" align="center">
        <template #default="scope">
          <el-button
            type="primary"
            link
            @click="openDetail(scope.row.id)"
            v-hasPermi="['infra:job:query']"
          >
            Detalhes
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页组件 -->
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 表单弹窗：查看 -->
  <JobLogDetail ref="detailRef" />
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import download from '@/utils/download'
import JobLogDetail from './JobLogDetail.vue'
import * as JobLogApi from '@/api/infra/jobLog'

defineOptions({ name: 'InfraJobLog' })

const message = useMessage() // 消息弹窗
const { query } = useRoute() // 查询参数

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  jobId: query.id,
  handlerName: undefined,
  beginTime: undefined,
  endTime: undefined,
  status: undefined
})
const queryFormRef = ref() // 搜索的表单
const exportLoading = ref(false) // 导出的加载中

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await JobLogApi.getJobLogPage(queryParams)
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

/** 查看操作 */
const detailRef = ref()
const openDetail = (rowId?: number) => {
  detailRef.value.open(rowId)
}

/** 导出按钮操作 */
const handleExport = async () => {
  try {
    // 导出的二次确认
    await message.exportConfirm()
    // 发起导出
    exportLoading.value = true
    const data = await JobLogApi.exportJobLog(queryParams)
    download.excel(data, 'log-tarefas-agendadas.xls')
  } catch {
  } finally {
    exportLoading.value = false
  }
}

/** 初始化 **/
onMounted(() => {
  getList()
})
</script>
