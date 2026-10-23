<template>
  <Dialog v-model="dialogVisible" :max-height="500" :scroll="true" title="Detalhes" width="800">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="Nº do log" min-width="120">
        {{ detailData.id }}
      </el-descriptions-item>
      <el-descriptions-item label="Rastreamento">
        {{ detailData.traceId }}
      </el-descriptions-item>
      <el-descriptions-item label="Aplicação">
        {{ detailData.applicationName }}
      </el-descriptions-item>
      <el-descriptions-item label="Usuário">
        {{ detailData.userId }}
        <dict-tag :type="DICT_TYPE.USER_TYPE" :value="detailData.userType" />
      </el-descriptions-item>
      <el-descriptions-item label="IP">
        {{ detailData.userIp }}
      </el-descriptions-item>
      <el-descriptions-item label="Navegador">
        {{ detailData.userAgent }}
      </el-descriptions-item>
      <el-descriptions-item label="Requisição">
        {{ detailData.requestMethod }} {{ detailData.requestUrl }}
      </el-descriptions-item>
      <el-descriptions-item label="Parâmetros">
        {{ detailData.requestParams }}
      </el-descriptions-item>
      <el-descriptions-item label="Resposta">
        {{ detailData.responseBody }}
      </el-descriptions-item>
      <el-descriptions-item label="Data">
        {{ formatDate(detailData.beginTime) }} ~ {{ formatDate(detailData.endTime) }}
      </el-descriptions-item>
      <el-descriptions-item label="Duração">{{ detailData.duration }} ms</el-descriptions-item>
      <el-descriptions-item label="Resultado">
        <div v-if="detailData.resultCode === 0">Ativo</div>
        <div v-else-if="detailData.resultCode > 0">
          Falha | {{ detailData.resultCode }} | {{ detailData.resultMsg }}
        </div>
      </el-descriptions-item>
      <el-descriptions-item label="Módulo">
        {{ detailData.operateModule }}
      </el-descriptions-item>
      <el-descriptions-item label="Operação">
        {{ detailData.operateName }}
      </el-descriptions-item>
      <el-descriptions-item label="Operação">
        <dict-tag :type="DICT_TYPE.INFRA_OPERATE_TYPE" :value="detailData.operateType" />
      </el-descriptions-item>
    </el-descriptions>
  </Dialog>
</template>

<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import * as ApiAccessLog from '@/api/infra/apiAccessLog'

defineOptions({ name: 'ApiAccessLogDetail' })

const dialogVisible = ref(false) // 弹窗的是否展示
const detailLoading = ref(false) // 表单地加载中
const detailData = ref({} as ApiAccessLog.ApiAccessLogVO) // 详情数据

/** 打开弹窗 */
const open = async (data: ApiAccessLog.ApiAccessLogVO) => {
  dialogVisible.value = true
  // 设置数据
  detailLoading.value = true
  try {
    detailData.value = data
  } finally {
    detailLoading.value = false
  }
}

defineExpose({ open }) // 提供 open 方法，用于打开弹窗
</script>
