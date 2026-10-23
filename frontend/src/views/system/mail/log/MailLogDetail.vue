<template>
  <Dialog v-model="dialogVisible" :max-height="500" :scroll="true" title="Detalhes" width="800">
    <el-descriptions v-loading="detailLoading" :column="1" border>
      <el-descriptions-item label="Nº do log" min-width="120">
        {{ detailData.id }}
      </el-descriptions-item>
      <el-descriptions-item label="Conta de e-mail">
        {{ accountList.find((account) => account.id === detailData.accountId)?.mail }}
      </el-descriptions-item>
      <el-descriptions-item label="Modelo de e-mail">
        {{ detailData.templateId }} | {{ detailData.templateCode }}
      </el-descriptions-item>
      <el-descriptions-item label="Remetente do modelo">
        {{ detailData.templateNickname }}
      </el-descriptions-item>
      <el-descriptions-item label="Destinatário">
        <span v-if="detailData.userType && detailData.userId">
          <dict-tag :type="DICT_TYPE.USER_TYPE" :value="detailData.userType" />
          ({{ detailData.userId }})
        </span>
        <span v-else>Nenhum</span>
      </el-descriptions-item>
      <el-descriptions-item label="Destinatários">
        <div>
          <div v-if="detailData.toMails && detailData.toMails.length > 0">
            Para: 
            <span v-for="(mail, index) in detailData.toMails" :key="mail">
              {{ mail }}<span v-if="Number(index) < detailData.toMails.length - 1">, </span>
            </span>
          </div>
          <div v-if="detailData.ccMails && detailData.ccMails.length > 0">
            Cc: 
            <span v-for="(mail, index) in detailData.ccMails" :key="mail">
              {{ mail }}<span v-if="Number(index) < detailData.ccMails.length - 1">, </span>
            </span>
          </div>
          <div v-if="detailData.bccMails && detailData.bccMails.length > 0">
            Cco: 
            <span v-for="(mail, index) in detailData.bccMails" :key="mail">
              {{ mail }}<span v-if="Number(index) < detailData.bccMails.length - 1">, </span>
            </span>
          </div>
        </div>
      </el-descriptions-item>
      <el-descriptions-item label="Assunto">
        {{ detailData.templateTitle }}
      </el-descriptions-item>
      <el-descriptions-item label="Conteúdo">
        <div v-dompurify-html="detailData.templateContent"></div>
      </el-descriptions-item>
      <el-descriptions-item label="Parâmetros">
        {{ detailData.templateParams }}
      </el-descriptions-item>
      <el-descriptions-item label="Criado em">
        {{ formatDate(detailData.createTime) }}
      </el-descriptions-item>
      <el-descriptions-item label="Situação do envio">
        <dict-tag :type="DICT_TYPE.SYSTEM_MAIL_SEND_STATUS" :value="detailData.sendStatus" />
      </el-descriptions-item>
      <el-descriptions-item label="Enviada em">
        {{ formatDate(detailData.sendTime) }}
      </el-descriptions-item>
      <el-descriptions-item label="ID da mensagem no servidor">
        {{ detailData.sendMessageId }}
      </el-descriptions-item>
      <el-descriptions-item label="Erro no envio">
        {{ detailData.sendException }}
      </el-descriptions-item>
    </el-descriptions>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import * as MailLogApi from '@/api/system/mail/log'
import * as MailAccountApi from '@/api/system/mail/account'

defineOptions({ name: 'SystemMailLogDetail' })

const dialogVisible = ref(false) // 弹窗的是否展示
const detailLoading = ref(false) // 表单的加载中
const detailData = ref({} as MailLogApi.MailLogVO) // 详情数据
const accountList = ref<MailAccountApi.MailAccountVO[]>([]) // 邮箱账号列表

/** 打开弹窗 */
const open = async (id: number) => {
  dialogVisible.value = true
  // 设置数据
  detailLoading.value = true
  try {
    detailData.value = {} as MailLogApi.MailLogVO
    detailData.value = await MailLogApi.getMailLog(id)
    accountList.value = await MailAccountApi.getSimpleMailAccountList()
  } finally {
    detailLoading.value = false
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗
</script>
