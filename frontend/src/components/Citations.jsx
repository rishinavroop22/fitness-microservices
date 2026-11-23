import ArticleIcon from "@mui/icons-material/ArticleOutlined";
import BuildIcon from "@mui/icons-material/BuildOutlined";
import { Box, Chip, Tooltip, Typography } from "@mui/material";

/** Sources the answer actually cited, numbered to match the [n] markers in the text. */
export default function Citations({ citations }) {
  if (!citations?.length) return null;
  return (
    <Box sx={{ mt: 1.5 }}>
      <Typography variant="caption" color="text.secondary">Sources</Typography>
      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.75, mt: 0.5 }}>
        {citations.map((c) => (
          <Tooltip key={`${c.number}-${c.chunkId ?? c.title}`} arrow
                   title={<span style={{ whiteSpace: "pre-wrap" }}>{c.snippet}</span>}>
            <Chip size="small" variant="outlined"
                  icon={c.type === "tool" ? <BuildIcon /> : <ArticleIcon />}
                  label={`[${c.number}] ${c.title}${c.chunkIndex !== undefined ? ` · part ${c.chunkIndex + 1}` : ""}`} />
          </Tooltip>
        ))}
      </Box>
    </Box>
  );
}
