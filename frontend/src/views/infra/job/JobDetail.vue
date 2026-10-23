<template>
  <Dialog v-model="dialogVisible" title="Detalhes da tarefa" width="700px">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="Nº" min-width="60">
        {{ detailData.id }}
      </el-descriptions-item>
      <el-descriptions-item label="Nome da tarefa">
        {{ detailData.name }}
      </el-descriptions-item>
      <el-descriptions-item label="Situação">
        <dict-tag :type="DICT_TYPE.INFRA_JOB_STATUS" :value="detailData.status" />
      </el-descriptions-item>
      <el-descriptions-item label="Handler">
        {{ detailData.handlerName }}
      </el-descriptions-item>
      <el-descriptions-item label="Parâmetros do handler">
        {{ detailData.handlerParam }}
      </el-descriptions-item>
      <el-descriptions-item label="Expressão CRON">
        {{ detailData.cronExpression }}
      </el-descriptions-item>
      <el-descriptions-item label="Tentativas">
        {{ detailData.retryCount }}
      </el-descriptions-item>
      <el-descriptions-item label="Intervalo entre tentativas">
        {{ detailData.retryInterval + ' ms' }}
      </el-descriptions-item>
      <el-descriptions-item label="Tempo limite">
        {{ detailData.monitorTimeout > 0 ? detailData.monitorTimeout + ' ms' : 'Desligado' }}
      </el-descriptions-item>
      <el-descriptions-item label="Próximas execuções">
        <el-timeline>
          <el-timeline-item
            v-for="(nextTime, index) in nextTimes"
            :key="index"
            :timestamp="formatDate(nextTime)"
          >
            {{ index + 1 }}ª
          </el-timeline-item>
        </el-timeline>
      </el-descriptions-item>
    </el-descriptions>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import * as JobApi from '@/api/infra/job'

defineOptions({ name: 'InfraJobDetail' })

const dialogVisible = ref(false) // 弹窗的是否展示
const detailLoading = ref(false) // 表单的加载中
const detailData = ref({} as JobApi.JobVO) // 详情数据
const nextTimes = ref([]) // 下一轮执行时间的数组

/** 打开弹窗 */
const open = async (id: number) => {
  dialogVisible.value = true
  // 查看，设置数据
  if (id) {
    detailLoading.value = true
    try {
      detailData.value = await JobApi.getJob(id)
      // 获取下一次执行时间
      nextTimes.value = await JobApi.getJobNextTimes(id)
    } finally {
      detailLoading.value = false
    }
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗
</script>
