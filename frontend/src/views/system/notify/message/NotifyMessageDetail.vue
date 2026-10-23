<template>
  <Dialog v-model="dialogVisible" :max-height="500" :scroll="true" title="Detalhes">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="Nº" min-width="120">
        {{ detailData.id }}
      </el-descriptions-item>
      <el-descriptions-item label="Tipo de usuário">
        <dict-tag :type="DICT_TYPE.USER_TYPE" :value="detailData.userType" />
      </el-descriptions-item>
      <el-descriptions-item label="Nº">
        {{ detailData.userId }}
      </el-descriptions-item>
      <el-descriptions-item label="Nº do modelo">
        {{ detailData.templateId }}
      </el-descriptions-item>
      <el-descriptions-item label="Código do modelo">
        {{ detailData.templateCode }}
      </el-descriptions-item>
      <el-descriptions-item label="Remetente">
        {{ detailData.templateNickname }}
      </el-descriptions-item>
      <el-descriptions-item label="Conteúdo do modelo">
        {{ detailData.templateContent }}
      </el-descriptions-item>
      <el-descriptions-item label="Parâmetros do modelo">
        {{ detailData.templateParams }}
      </el-descriptions-item>
      <el-descriptions-item label="Tipo do modelo">
        <dict-tag :type="DICT_TYPE.SYSTEM_NOTIFY_TEMPLATE_TYPE" :value="detailData.templateType" />
      </el-descriptions-item>
      <el-descriptions-item label="Lida">
        <dict-tag :type="DICT_TYPE.INFRA_BOOLEAN_STRING" :value="detailData.readStatus" />
      </el-descriptions-item>
      <el-descriptions-item label="Lida em">
        {{ formatDate(detailData.readTime) }}
      </el-descriptions-item>
      <el-descriptions-item label="Criado em">
        {{ formatDate(detailData.createTime) }}
      </el-descriptions-item>
    </el-descriptions>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import * as NotifyMessageApi from '@/api/system/notify/message'

defineOptions({ name: 'SystemNotifyMessageDetail' })

const dialogVisible = ref(false) // 弹窗的是否展示
const detailLoading = ref(false) // 表单的加载中
const detailData = ref({} as NotifyMessageApi.NotifyMessageVO) // 详情数据

/** 打开弹窗 */
const open = async (data: NotifyMessageApi.NotifyMessageVO) => {
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
