<template>
  <doc-alert title="Envio e download" url="https://doc.iocoder.cn/file/" />
  <!-- 搜索 -->
  <ContentWrap>
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="68px"
    >
      <el-form-item label="Caminho" prop="path">
        <el-input
          v-model="queryParams.path"
          placeholder="Digite o caminho"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="Tipo" prop="type" width="80">
        <el-input
          v-model="queryParams.type"
          placeholder="Digite o tipo"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
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
        <el-button type="primary" plain @click="openForm">
          <Icon icon="ep:upload" class="mr-5px" /> Enviar arquivo
        </el-button>
        <el-button
          type="danger"
          plain
          :disabled="checkedIds.length === 0"
          @click="handleDeleteBatch"
          v-hasPermi="['infra:file:delete']"
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
      <el-table-column label="Nome do arquivo" align="center" prop="name" :show-overflow-tooltip="true" />
      <el-table-column label="Caminho" align="center" prop="path" :show-overflow-tooltip="true" />
      <el-table-column label="URL" align="center" prop="url" :show-overflow-tooltip="true" />
      <el-table-column
        label="Tamanho"
        align="center"
        prop="size"
        width="120"
        :formatter="fileSizeFormatter"
      />
      <el-table-column label="Tipo" align="center" prop="type" width="180px" />
      <el-table-column label="Conteúdo" align="center" prop="url" width="110px">
        <template #default="{ row }">
          <el-image
            v-if="row.type.includes('image')"
            class="h-80px w-80px"
            lazy
            :src="row.url"
            :preview-src-list="[row.url]"
            preview-teleported
            fit="cover"
          />
          <el-link
            v-else-if="row.type.includes('pdf')"
            type="primary"
            :href="row.url"
            :underline="false"
            target="_blank"
          >
            Visualizar
          </el-link>
          <el-link
            v-else
            type="primary"
            download
            :href="row.url"
            :underline="false"
            target="_blank"
          >
            Baixar
          </el-link>
        </template>
      </el-table-column>
      <el-table-column
        label="Enviado em"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="Ações" align="center">
        <template #default="scope">
          <el-button link type="primary" @click="copyToClipboard(scope.row.url)">
            Copiar link
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['infra:file:delete']"
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
  <FileForm ref="formRef" @success="getList" />
</template>
<script lang="ts" setup>
import { fileSizeFormatter } from '@/utils'
import { dateFormatter } from '@/utils/formatTime'
import * as FileApi from '@/api/infra/file'
import FileForm from './FileForm.vue'
import { useClipboard } from '@vueuse/core'

defineOptions({ name: 'InfraFile' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  type: undefined,
  path: undefined,
  createTime: []
})
const queryFormRef = ref() // 搜索的表单

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await FileApi.getFilePage(queryParams)
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
const openForm = () => {
  formRef.value.open()
}

/** 复制到剪贴板方法 */
const copyToClipboard = async (text: string) => {
  const { copy, copied, isSupported } = useClipboard({ legacy: true, source: text })
  if (!isSupported) {
    message.error(t('common.copyError'))
    return
  }
  await copy()
  if (unref(copied)) {
    message.success(t('common.copySuccess'))
  }
}

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起删除
    await FileApi.deleteFile(id)
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 批量删除按钮操作 */
const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows) => {
  checkedIds.value = rows.map((row) => row.id)
}

const handleDeleteBatch = async () => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起批量删除
    await FileApi.deleteFileList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 初始化 **/
onMounted(() => {
  getList()
})
</script>
