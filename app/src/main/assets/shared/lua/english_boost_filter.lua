-- 中文“混乱”时让英文优先。
--
-- 判断依据是 Rime 给候选打的类型：整段输入在词库里找不到完整的词时，首选候选是
-- 引擎临时拼出来的句子（type == "sentence"）。这时如果有一个英文候选正好覆盖到输入
-- 末尾，就把它提到第一位。词库里有完整中文词（type 为 phrase / user_phrase）时一律不动。
-- 26 键和九宫格通用：九宫格里输入是数字串，英文词典同样按数字匹配。

local M = {}

local function is_english(text)
  -- 纯 ASCII 的单词或缩写，至少含一个字母
  return text:match("^[%w%-%.'@]+$") ~= nil and text:match("%a") ~= nil
end

function M.init(env)
end

function M.func(input, env)
  local raw = env.engine.context.input
  local raw_len = #raw
  -- input:iter() 返回的是 (迭代函数, 状态) 两个值，手动调用时必须把状态传回去
  local next_cand, state = input:iter()

  -- 只看前面一批候选，剩下的原样透传
  local buf = {}
  for _ = 1, 24 do
    local cand = next_cand(state)
    if not cand then break end
    buf[#buf + 1] = cand
  end

  local promote = nil
  if buf[1] and buf[1].type == "sentence" then
    for _, cand in ipairs(buf) do
      local text = cand.text
      if #text >= 2 and is_english(text) and cand._end >= raw_len then
        promote = cand
        break
      end
    end
  end

  if promote then yield(promote) end
  for _, cand in ipairs(buf) do
    if cand ~= promote then yield(cand) end
  end
  for cand in next_cand, state do
    yield(cand)
  end
end

return M
