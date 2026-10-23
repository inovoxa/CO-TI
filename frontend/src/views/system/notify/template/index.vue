<template>
  <doc-alert title="Configuração de mensagens internas" url="https://doc.iocoder.cn/notify/" />

  <!-- 搜索工作栏 -->
  <ContentWrap>
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="68px"
    >
      <el-form-item label="Nome do modelo" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="Digite o nome do modelo"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="Nº do modelo" prop="code">
        <el-input
          v-model="queryParams.code"
          placeholder="Digite o código do modelo"
          clearable
          @keyup.enter="handleQuery"
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
            v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Criado em" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="daterange"
          start-placeholder="Data inicial"
          end-placeholder="Data final"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> Buscar</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> Limpar</el-button>
        <el-button
          type="primary"
          plain
          @click="openForm('create')"
          v-hasPermi="['system:notify-template:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" />Novo
        </el-button>
        <el-button
          type="danger"
          plain
          :disabled="checkedIds.length === 0"
          @click="handleDeleteBatch"
          v-hasPermi="['system:notify-template:delete']"
        >
          <Icon icon="ep:delete" class="mr-5px" />Excluir selecionados
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="handleRowCheckboxChange">
      <el-table-column type="selection" width="55" />
      <el-table-column
        label="Código do modelo"
        align="center"
        prop="code"
        width="120"
        :show-overflow-tooltip="true"
      />
      <el-table-column
        label="Nome do modelo"
        align="center"
        prop="name"
        width="120"
        :show-overflow-tooltip="true"
      />
      <el-table-column label="Tipo" align="center" prop="type">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.SYSTEM_NOTIFY_TEMPLATE_TYPE" :value="scope.row.type" />
        </template>
      </el-table-column>
      <el-table-column label="Remetente" align="center" prop="nickname" />
      <el-table-column
        label="Conteúdo do modelo"
        align="center"
        prop="content"
        width="200"
        :show-overflow-tooltip="true"
      />
      <el-table-column label="Situação" align="center" prop="status" width="80">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="Observações" align="center" prop="remark" />
      <el-table-column
        label="Criado em"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="Ações" align="center" width="210" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['system:notify-template:update']"
          >
            Editar
          </el-button>
          <el-button
            link
            type="primary"
            @click="openSendForm(scope.row)"
            v-hasPermi="['system:notify-template:send-notify']"
          >
            Testar
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['system:notify-template:delete']"
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
  <NotifyTemplateForm ref="formRef" @success="getList" />
  <!-- 表单弹窗：测试发送 -->
  <NotifyTemplateSendForm ref="sendFormRef" />
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as NotifyTemplateApi from '@/api/system/notify/template'
import NotifyTemplateForm from './NotifyTemplateForm.vue'
import NotifyTemplateSendForm from './NotifyTemplateSendForm.vue'

defineOptions({ name: 'NotifySmsTemplate' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化

const loading = ref(false) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  status: undefined,
  code: undefined,
  createTime: []
})
const queryFormRef = ref() // 搜索的表单

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await NotifyTemplateApi.getNotifyTemplatePage(queryParams)
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

/** 添加/修改操作 */
const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起删除
    await NotifyTemplateApi.deleteNotifyTemplate(id)
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 批量删除按钮操作 */
const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows: NotifyTemplateApi.NotifyTemplateVO[]) => {
  checkedIds.value = rows.map((row) => row.id!)
}

const handleDeleteBatch = async () => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起批量删除
    await NotifyTemplateApi.deleteNotifyTemplateList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 发送站内信按钮 */
const sendFormRef = ref() // 表单 Ref
const openSendForm = (row: NotifyTemplateApi.NotifyTemplateVO) => {
  sendFormRef.value.open(row.id)
}

/** 初始化 **/
onMounted(() => {
  getList()
})
</script>
