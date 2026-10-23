<template>
  <doc-alert title="Configuração de e-mail" url="https://doc.iocoder.cn/mail" />

  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="100px"
    >
      <el-form-item label="E-mail do destinatário" prop="toMail">
        <el-input
          v-model="queryParams.toMail"
          placeholder="Digite o e-mail do destinatário"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="Conta de e-mail" prop="accountId">
        <el-select
          v-model="queryParams.accountId"
          placeholder="Selecione a conta de e-mail"
          clearable
          class="!w-240px"
        >
          <el-option
            v-for="account in accountList"
            :key="account.id"
            :value="account.id!"
            :label="account.mail"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Nº do modelo" prop="templateId">
        <el-input
          v-model="queryParams.templateId"
          placeholder="Digite o nº do modelo"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="Situação do envio" prop="sendStatus">
        <el-select
          v-model="queryParams.sendStatus"
          placeholder="Selecione a situação do envio"
          clearable
          class="!w-240px"
        >
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.SYSTEM_MAIL_SEND_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Nº" prop="userId">
        <el-input
          v-model="queryParams.userId"
          placeholder="Digite o nº do usuário"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="Tipo de usuário" prop="userType">
        <el-select
          v-model="queryParams.userType"
          placeholder="Selecione o tipo de usuário"
          clearable
          class="!w-240px"
        >
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.USER_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Enviada em" prop="sendTime">
        <el-date-picker
          v-model="queryParams.sendTime"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="daterange"
          start-placeholder="Data inicial"
          end-placeholder="Data final"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> Buscar</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> Limpar</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="Nº" align="center" prop="id" />
      <el-table-column
        label="Enviada em"
        align="center"
        prop="sendTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="Destinatário" align="center" width="150">
        <template #default="scope">
          <div v-if="scope.row.userType && scope.row.userId">
            <dict-tag :type="DICT_TYPE.USER_TYPE" :value="scope.row.userType" />
            <div>{{ '(' + scope.row.userId + ')' }}</div>
          </div>
          <div v-else>-</div>
        </template>
      </el-table-column>
      <el-table-column label="Destinatários" align="center" width="300">
        <template #default="scope">
          <div class="text-left">
            <div v-if="scope.row.toMails && scope.row.toMails.length > 0">
              Para: 
              <span v-for="(mail, index) in scope.row.toMails" :key="mail">
                {{ mail }}<span v-if="Number(index) < scope.row.toMails.length - 1">, </span>
              </span>
            </div>
            <div v-if="scope.row.ccMails && scope.row.ccMails.length > 0">
              Cc: 
              <span v-for="(mail, index) in scope.row.ccMails" :key="mail">
                {{ mail }}<span v-if="Number(index) < scope.row.ccMails.length - 1">, </span>
              </span>
            </div>
            <div v-if="scope.row.bccMails && scope.row.bccMails.length > 0">
              Cco: 
              <span v-for="(mail, index) in scope.row.bccMails" :key="mail">
                {{ mail }}<span v-if="Number(index) < scope.row.bccMails.length - 1">, </span>
              </span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="Assunto" align="center" prop="templateTitle" width="200" />
      <el-table-column label="Situação do envio" align="center" width="120">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.SYSTEM_MAIL_SEND_STATUS" :value="scope.row.sendStatus" />
        </template>
      </el-table-column>
      <el-table-column label="Conta de e-mail" align="center" width="200">
        <template #default="scope">
          {{ getAccountMail(scope.row.accountId) }}
        </template>
      </el-table-column>
      <el-table-column label="Nº do modelo" align="center" prop="templateId" />
      <el-table-column label="Ações" align="center" fixed="right" class-name="fixed-width">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openDetail(scope.row)"
            v-hasPermi="['system:mail-log:query']"
          >
            Detalhes
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

  <!-- 表单弹窗：详情 -->
  <MailLogDetail ref="detailRef" />
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as MailAccountApi from '@/api/system/mail/account'
import * as MailLogApi from '@/api/system/mail/log'
import MailLogDetail from './MailLogDetail.vue'

defineOptions({ name: 'SystemMailLog' })

const loading = ref(false) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref([]) // 列表的数据
const queryFormRef = ref() // 搜索的表单
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  toMail: '',
  accountId: undefined,
  templateId: undefined,
  sendStatus: undefined,
  userId: undefined,
  userType: undefined,
  sendTime: []
})
const accountList = ref<MailAccountApi.MailAccountVO[]>([]) // 邮箱账号列表

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await MailLogApi.getMailLogPage(queryParams)
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

/** 详情操作 */
const detailRef = ref()
const openDetail = (data: MailLogApi.MailLogVO) => {
  detailRef.value.open(data.id)
}

/** 获取邮箱账号名称 */
const getAccountMail = (accountId: number) => {
  const account = accountList.value.find((account) => account.id === accountId)
  return account?.mail || ''
}

/** 初始化 **/
onMounted(async () => {
  await getList()
  // 加载邮箱账号列表
  accountList.value = await MailAccountApi.getSimpleMailAccountList()
})
</script>
