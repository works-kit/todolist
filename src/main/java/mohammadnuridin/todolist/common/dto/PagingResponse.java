package mohammadnuridin.todolist.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PagingResponse {

    private Integer page;

    private Integer size;

    private Integer totalItems;

    private Integer totalPages;
}